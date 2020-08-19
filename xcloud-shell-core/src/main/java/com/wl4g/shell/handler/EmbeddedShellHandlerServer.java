/*
 * Copyright 2017 ~ 2025 the original author or authors. <wanglsir@gmail.com, 983708408@qq.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.shell.handler;

import com.wl4g.shell.config.ShellProperties;
import com.wl4g.shell.handler.SignalChannelHandler;
import com.wl4g.shell.registry.ShellHandlerRegistrar;
import com.wl4g.shell.registry.TargetMethodWrapper;
import com.wl4g.shell.signal.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.wl4g.components.common.lang.Assert2.notNullOf;
import static com.wl4g.components.common.lang.Assert2.state;
import static com.wl4g.shell.signal.ChannelState.*;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.exception.ExceptionUtils.*;

/**
 * Embedded shell handle server
 * 
 * @author Wangl.sir <983708408@qq.com>
 * @version v1.0 2019年5月1日
 * @since
 */
public class EmbeddedShellHandlerServer extends ServerShellHandler implements Runnable {

	/**
	 * Current server shellRunning status.
	 */
	final private AtomicBoolean running = new AtomicBoolean(false);

	/** Command channel workers. */
	final private Map<ServerSignalChannelHandler, Thread> channels = new ConcurrentHashMap<>();

	/**
	 * Server sockets
	 */
	private ServerSocket ss;

	/**
	 * Boss thread
	 */
	private Thread boss;

	public EmbeddedShellHandlerServer(ShellProperties config, String appName, ShellHandlerRegistrar registrar) {
		super(config, appName, registrar);
	}

	/**
	 * Start server shell handler instance
	 * 
	 * @throws Exception
	 */
	public void start() throws Exception {
		if (running.compareAndSet(false, true)) {
			state(isNull(ss), "server socket already listen ?");

			// Determine server port.
			int bindPort = ensureDetermineServPort(getAppName());

			ss = new ServerSocket(bindPort, getConfig().getBacklog(), getConfig().getInetBindAddr());
			ss.setSoTimeout(0); // Infinite timeout
			log.info("Shell Console started on port(s): {}", bindPort);

			boss = new Thread(this, getClass().getSimpleName() + "-boss");
			boss.setDaemon(true);
			boss.start();
		}
	}

	@Override
	public void close() {
		if (running.compareAndSet(true, false)) {
			try {
				boss.interrupt();
			} catch (Exception e) {
				log.error("Interrupting boss failure", e);
			}

			if (ss != null && !ss.isClosed()) {
				try {
					ss.close();
				} catch (IOException e) {
					log.error("Closing server failure", e);
				}
			}

			Iterator<ServerSignalChannelHandler> it = channels.keySet().iterator();
			while (it.hasNext()) {
				try {
					ServerSignalChannelHandler h = it.next();
					Thread t = channels.get(h);
					t.interrupt();
					t = null;
					it.remove();
				} catch (Exception e) {
					log.error("Closing worker failure", e);
				}
			}
		}
	}

	/**
	 * Accepting connect processing
	 */
	@Override
	public void run() {
		while (!boss.isInterrupted() && running.get()) {
			try {
				// Receiving client socket(blocking)
				Socket s = ss.accept();
				log.debug("On accept socket: {}, maximum: {}, actual: {}", s, getConfig().getMaxClients(), channels.size());

				// Check many connections.
				if (channels.size() >= getConfig().getMaxClients()) {
					log.warn(String.format("There are too many parallel shell connections. maximum: %s, actual: %s",
							getConfig().getMaxClients(), channels.size()));
					s.close();
					continue;
				}

				// Create shell channel
				ServerSignalChannelHandler channel = new ServerSignalChannelHandler(registrar, s, line -> process(line));

				// MARK1:
				// The worker thread may not be the parent thread of Runnable,
				// so you need to display bind to the thread in the afternoon
				// again.
				Thread channelTask = new Thread(() -> bind(channel).run(),
						getClass().getSimpleName() + "-channel-" + channels.size());
				channelTask.setDaemon(true);
				channels.put(channel, channelTask);
				channelTask.start();

			} catch (Throwable e) {
				log.warn("Shell boss thread shutdown. cause: {}", getStackTrace(e));
			}
		}
	}

	@Override
	protected void preHandleInput(TargetMethodWrapper tm, List<Object> args) {
		// Get current context
		BaseShellContext context = getClient().getContext();

		// Bind target method
		context.setTarget(tm);

		// Resolving args with {@link AbstractShellContext}
		BaseShellContext updateContext = resolveInjectArgsForShellContextIfNecceary(context, tm, args);
		// Inject update actual context
		getClient().setContext(updateContext);
	}

	/**
	 * If necessary, resolving whether the shell method parameters have
	 * {@link BaseShellContext} instances and inject.
	 * 
	 * @param context
	 * @param tm
	 * @param args
	 */
	private BaseShellContext resolveInjectArgsForShellContextIfNecceary(BaseShellContext context, TargetMethodWrapper tm,
			List<Object> args) {

		// Find parameter: ShellContext index and class
		Object[] ret = findParameterForShellContext(tm);
		int index = (int) ret[0];
		Class<?> contextClass = (Class<?>) ret[1];

		if (index >= 0) { // have ShellContext?
			// Convert to specific shellContext
			if (SimpleShellContext.class.isAssignableFrom(contextClass)) {
				context = new SimpleShellContext(context);
			} else if (ProgressShellContext.class.isAssignableFrom(contextClass)) {
				context = new ProgressShellContext(context);
			}
			if (index < args.size()) { // Correct parameter index
				args.add(index, context);
			} else {
				args.add(context);
			}

			/**
			 * When injection {@link ShellContext} is used, the auto open
			 * channel status is wait.
			 */
			context.begin(); // MARK2
		}

		return context;
	}

	/**
	 * Get {@link ShellContext} index by parameters classes.
	 * 
	 * @param tm
	 * @param clazz
	 * @return
	 */
	private Object[] findParameterForShellContext(TargetMethodWrapper tm) {
		int index = -1, i = 0;
		Class<?> contextCls = null;
		for (Class<?> cls : tm.getMethod().getParameterTypes()) {
			if (ShellContext.class.isAssignableFrom(cls)) {
				state(index < 0, format("Multiple shellcontext type parameters are unsupported. %s", tm.getMethod()));
				index = i;
				contextCls = cls;
			}
			++i;
		}
		return new Object[] { index, contextCls };
	}

	/**
	 * Server shell signal channel handler
	 * 
	 * @author Wangl.sir <983708408@qq.com>
	 * @version v1.0 2019年5月2日
	 * @since
	 */
	class ServerSignalChannelHandler extends SignalChannelHandler {

		/** Running current command process worker */
		private final ExecutorService processWorker;

		/** Running current command {@link ShellContext} */
		private BaseShellContext currentContext;

		public ServerSignalChannelHandler(ShellHandlerRegistrar registrar, Socket client, Function<String, Object> func) {
			super(registrar, client, func);
			this.currentContext = new BaseShellContext(this) {
			};
			// Init worker
			final AtomicInteger incr = new AtomicInteger(0);
			this.processWorker = new ThreadPoolExecutor(1, 1, 0, SECONDS, new LinkedBlockingDeque<>(1), r -> {
				String prefix = getClass().getSimpleName() + "-worker-" + incr.incrementAndGet();
				Thread t = new Thread(r, prefix);
				t.setDaemon(true);
				return t;
			});
		}

		BaseShellContext getContext() {
			return currentContext;
		}

		void setContext(BaseShellContext context) {
			notNullOf(context, "ShellContext");
			this.currentContext = context;
		}

		@Override
		public void run() {
			while (running.get() && isActive()) {
				try {
					Object stdin = new ObjectInputStream(_in).readObject();
					log.info("<= {}", stdin);

					Object output = null;
					// Register shell methods
					if (stdin instanceof MetaSignal) {
						output = new MetaSignal(registrar.getTargetMethods());
					}
					// Ask interruption
					else if (stdin instanceof PreInterruptSignal) {
						// Call pre-interrupt events.
						currentContext.getUnmodifiableEventListeners().forEach(l -> l.onPreInterrupt(currentContext));
						// Ask if the client is interrupt.
						output = new AskInterruptSignal("Are you sure you want to cancel execution? (y|n)");
					}
					// Confirm interruption
					else if (stdin instanceof AckInterruptSignal) {
						AckInterruptSignal ack = (AckInterruptSignal) stdin;
						// Call interrupt events.
						currentContext.getUnmodifiableEventListeners()
								.forEach(l -> l.onInterrupt(currentContext, ack.getConfirm()));
					}
					// Stdin of commands
					else if (stdin instanceof StdinSignal) {
						StdinSignal cmd = (StdinSignal) stdin;
						// Call command events.
						currentContext.getUnmodifiableEventListeners().forEach(l -> l.onCommand(currentContext, cmd.getLine()));

						// Resolve that client input cannot be received during
						// blocking execution.
						processWorker.execute(() -> {
							try {
								/**
								 * Only {@link ShellContext} printouts are
								 * supported, and return value is no longer
								 * supported (otherwise it will be ignored)
								 */
								function.apply(cmd.getLine());

								/**
								 * see:{@link EmbeddedServerShellHandler#preHandleInput()}#MARK2
								 */
								if (currentContext.getState() != RUNNING) {
									currentContext.completed();
								}
							} catch (Throwable e) {
								log.error(format("Failed to handle shell command: [%s]", cmd.getLine()), e);
								handleError(e);
							}
						});
					}

					if (nonNull(output)) { // Write to console.
						currentContext.printf0(output);
					}
				} catch (Throwable th) {
					handleError(th);
				} finally {
					try {
						sleep(100L);
					} catch (InterruptedException e) {
					}
				}
			}
		}

		@Override
		public void close() throws IOException {
			// Prevent threadContext memory leakage.
			cleanup();

			// Close the current socket
			super.close();

			// Clear the current channel
			Thread t = channels.remove(this);
			if (t != null) {
				t.interrupt();
				t = null;
			}
			log.debug("Remove shellHandler: {}, actual: {}", this, channels.size());
		}

		/**
		 * Error handling
		 * 
		 * @param th
		 */
		private void handleError(Throwable th) {
			if ((th instanceof SocketException) || (th instanceof EOFException) || !isActive()) {
				log.warn("Disconnect for client : {}", socket);
				try {
					close();
				} catch (IOException e) {
					log.error("Close failure.", e);
				}
			} else {
				currentContext.printf0(th);
			}
		}

	}

}