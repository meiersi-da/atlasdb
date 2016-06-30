/**
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.dropwizard.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;

import org.codehaus.groovy.tools.shell.util.NoExitSecurityManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.palantir.atlasdb.dropwizard.FullAccessNoExitSecurityManager;
import com.palantir.atlasdb.dropwizard.TestConfig;
import com.palantir.atlasdb.dropwizard.cli.CliResult;
import com.palantir.atlasdb.dropwizard.cli.CliUtils;

public class AtlasDbConsoleCommandTest {
    private static final String CONFIG_FILE = AtlasDbConsoleCommandTest.class.getResource("/testConfig.yml").toExternalForm();

    private SecurityManager oldSecurityManager;

    @Before
    public void disableSystemExit() {
        oldSecurityManager = System.getSecurityManager();
        AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            System.setSecurityManager(new FullAccessNoExitSecurityManager());
            return null;
        });
    }

    @After
    public void revertSecurityManagerChange() {
        System.setSecurityManager(oldSecurityManager);
    }

    @Test
    public void canCallConsoleHelp() throws Exception {
        CliResult result = CliUtils.run(
                ImmutableList.of(new AtlasDbConsoleCommand<>(TestConfig.class)),
                "console", "--bind", "a", "VALUE_TO_FIND", "--evaluate", "println a", CONFIG_FILE);
        assertThat(result.wasSuccessful())
                .as(CliUtils.getFancyCliDescription("Successful run", result))
                .isEqualTo(true);
        assertThat(result.getStandardOutput())
                .as(CliUtils.getFancyCliDescription("Printed bind value", result))
                .contains("VALUE_TO_FIND");
    }
}
