package com.ymdelf.utils;

public final class CommandResult {
    public final int exitCode;
    public final String out;
    public final String err;
    CommandResult(int c, String o, String e) { exitCode = c; out = o; err = e; }
    public boolean isSuccess() { return exitCode == 0; }
}

