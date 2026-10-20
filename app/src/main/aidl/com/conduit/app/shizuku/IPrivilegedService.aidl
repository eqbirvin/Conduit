package com.conduit.app.shizuku;

// Runs inside a Shizuku-spawned process with shell (adb) or root identity.
interface IPrivilegedService {
    // Reserved by Shizuku: called when the service is unbound with remove = true.
    void destroy() = 16777114;

    // Runs the command without a shell and returns its exit code (-1 if it could not start).
    int exec(in String[] command) = 1;
}
