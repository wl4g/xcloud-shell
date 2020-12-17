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
package com.wl4g.shell.core;

import static com.wl4g.component.common.lang.Assert2.hasTextOf;
import static com.wl4g.component.common.lang.Assert2.notNullOf;
import static java.util.Objects.nonNull;

import com.wl4g.shell.common.registry.ShellHandlerRegistrar;
import com.wl4g.shell.core.config.ShellProperties;
import com.wl4g.shell.core.handler.EmbeddedShellServer;

/**
 * Budiler of {@link EmbeddedShellServer}
 *
 * @author Wangl.sir <wanglsir@gmail.com, 983708408@qq.com>
 * @version v1.0 2020-08-10
 * @since
 */
public class EmbeddedShellServerBuilder {

	/** Shell application name */
	private String appName = "defaultShellApplication";

	/** {@link ShellProperties} */
	private ShellProperties config = new ShellProperties();

	/** {@link ShellHandlerRegistrar} */
	private ShellHandlerRegistrar registrar = new ShellHandlerRegistrar();

	private EmbeddedShellServerBuilder() {
	}

	/**
	 * New instantial of {@link EmbeddedShellServer}
	 * 
	 * @return
	 */
	public static EmbeddedShellServerBuilder newBuilder() {
		return new EmbeddedShellServerBuilder();
	}

	/**
	 * Sets shell application name.
	 * 
	 * @param appName
	 * @return
	 */
	public EmbeddedShellServerBuilder withAppName(String appName) {
		hasTextOf(appName, "appName");
		this.appName = appName;
		return this;
	}

	/**
	 * Sets shell configuration of {@link ShellProperties}.
	 * 
	 * @param appName
	 * @return
	 */
	public EmbeddedShellServerBuilder withConfiguration(ShellProperties config) {
		notNullOf(config, "config");
		this.config = config;
		return this;
	}

	/**
	 * Sets shell handler registry of {@link ShellHandlerRegistrar}.
	 * 
	 * @param appName
	 * @return
	 */
	public EmbeddedShellServerBuilder withRegistrar(ShellHandlerRegistrar registrar) {
		notNullOf(registrar, "registrar");
		this.registrar = registrar;
		return this;
	}

	/**
	 * Registration shell component instance.
	 * 
	 * @param shellComponents
	 * @return
	 */
	public EmbeddedShellServerBuilder register(Object... shellComponents) {
		if (nonNull(shellComponents)) {
			for (Object c : shellComponents) {
				this.registrar.register(c);
			}
		}
		return this;
	}

	public EmbeddedShellServer build() {
		return new EmbeddedShellServer(config, appName, registrar);
	}

}
