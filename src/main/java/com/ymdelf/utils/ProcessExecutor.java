package com.ymdelf.utils;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * 工具类：封装外部进程（如 git 命令）的执行逻辑。
 * 基于 Apache Commons Exec 实现，支持标准输出捕获、错误处理。
 * <p>
 * 示例：
 * <pre>
 *     String output = ProcessExecutor.exec("git", "init", "--bare", "myrepo.git");
 * </pre>
 */
public class ProcessExecutor {
    private ProcessExecutor() {
    }

    private static String errMsg(String[] cmd, CommandResult r) {
        return "Command '" + String.join(" ", cmd) + "' failed, exit " + r.exitCode +
                "\nSTDERR:\n" + r.err;
    }

    /**
     * 执行一个外部命令（命令参数形式传入），并返回标准输出结果。
     *
     * @param command 命令和参数，例如：["git", "status"]
     * @return 命令执行后的标准输出内容（UTF-8 编码）
     * @throws Exception 命令执行失败或退出码不为 0 时抛出异常
     */
    public static String exec(String... command) throws Exception {
        return execResult(command).out;
    }

    /**
     * 执行一个已构建好的 CommandLine 对象，并返回标准输出结果。
     *
     * @param commandLine Commons Exec 的命令行对象
     * @return 命令执行后的标准输出内容（UTF-8 编码）
     * @throws Exception 命令执行失败或退出码不为 0 时抛出异常
     */
    public static String exec(CommandLine commandLine) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PumpStreamHandler handler = new PumpStreamHandler(out);

        DefaultExecutor executor = DefaultExecutor.builder().get(); // 避免使用已弃用构造器
        executor.setStreamHandler(handler);

        int exitCode = executor.execute(commandLine);
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    public static String execOrThrow(String... command) throws Exception {
        CommandResult r = execResult(command);
        if (!r.isSuccess()) throw new RuntimeException(errMsg(command, r));
        return r.out;
    }

    public static CommandResult execResult(String... command) throws Exception {
        CommandLine cmd = new CommandLine(command[0])
                .addArguments(Arrays.copyOfRange(command, 1, command.length), false);
        return execResult(cmd, null, Collections.emptyMap());
    }
    public static CommandResult execResult(CommandLine command) throws Exception {
        return execResult(command, null, Collections.emptyMap());
    }

    public static CommandResult execResult(CommandLine cmd,
                                           File workDir,
                                           Map<String, String> extraEnv) throws Exception {

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PumpStreamHandler handler = new PumpStreamHandler(outBuf, errBuf);

        DefaultExecutor exec = workDir != null ?
                DefaultExecutor.builder().setWorkingDirectory(workDir).get() :
                DefaultExecutor.builder().get();

//        DefaultExecutor exec = DefaultExecutor.builder().get();
        exec.setStreamHandler(handler);
        exec.setExitValues(null);               // 不抛 ExecuteException

//        if (workDir != null) exec.setWorkingDirectory(workDir);

        // 合并自定义环境
        Map<String, String> env = System.getenv();
        if (!extraEnv.isEmpty()) {
            env = new java.util.HashMap<>(env);
            env.putAll(extraEnv);
        }

        int code = exec.execute(cmd, env);
        return new CommandResult(code,
                StringUtils.trim(outBuf.toString(StandardCharsets.UTF_8)),
                errBuf.toString(StandardCharsets.UTF_8));
    }


}
