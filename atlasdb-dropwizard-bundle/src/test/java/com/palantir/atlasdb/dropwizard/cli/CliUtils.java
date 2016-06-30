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
package com.palantir.atlasdb.dropwizard.cli;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.List;

import javax.annotation.Nullable;

import org.assertj.core.description.Description;
import org.assertj.core.description.TextDescription;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.palantir.atlasdb.dropwizard.TestConfig;

import io.dropwizard.Application;
import io.dropwizard.cli.Cli;
import io.dropwizard.cli.Command;
import io.dropwizard.configuration.UrlConfigurationSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;

public class CliUtils {
    public static CliResult run(List<Command> commandsToAdd, String... cliArgs) {
        final JarLocation location = mock(JarLocation.class);
        when(location.getVersion())
                .thenReturn(Optional.of("1.0.0"));

        final Bootstrap<TestConfig> bootstrap = new Bootstrap<>(mock(Application.class));
        bootstrap.setConfigurationSourceProvider(new UrlConfigurationSourceProvider());
        commandsToAdd.forEach(bootstrap::addCommand);

        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

        Cli cli = new Cli(location, bootstrap, stdOut, stdErr);

        try {
            //System.setOut(new PrintStream(stdOut));
            //System.setErr(new PrintStream(stdErr));
            //System.setIn(new ByteArrayInputStream(new byte[]{}));

            boolean success = cli.run(cliArgs);

            return ImmutableCliResult.builder()
                    .wasSuccessful(success)
                    .standardOutput(stdOut.toString())
                    .standardError(stdErr.toString())
                    .build();
        } catch (Exception e) {
            return ImmutableCliResult.builder()
                    .wasSuccessful(false)
                    .exception(e)
                    .standardOutput(stdOut.toString())
                    .standardError(stdErr.toString())
                    .build();
        } finally {
            System.setOut(null);
            System.setErr(null);
            System.setIn(null);
        }
    }

    public static Description getFancyCliDescription(String description, CliResult result) {
        return new TextDescription(description + "\n" +
                "Exception:\n%s\n" +
                "Standard Output:\n%s\n" +
                "Standard Error:\n%s",
                cleanupAndIndent(result.getException() != null ? Throwables.getStackTraceAsString(result.getException()) : null),
                cleanupAndIndent(result.getStandardOutput()),
                cleanupAndIndent(result.getStandardError()));
    }

    private static String cleanupAndIndent(@Nullable String message) {
        return MoreObjects.firstNonNull(message, "null").trim().replaceAll("(?m)^", "\t");
    }
}
