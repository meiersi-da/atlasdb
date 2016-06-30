package com.palantir.atlasdb.dropwizard;

import java.security.Permission;

public class FullAccessNoExitSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
    }

    @Override
    public void checkExit(int code) {
        throw new SecurityException("Use of System.exit() is forbidden!");
    }

    @Override
    public void checkRead(String file) {
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
    }

    @Override
    public void checkCreateClassLoader() {
    }
}
