package com.ymdelf;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.ymdelf.utils.CommandResult;
import com.ymdelf.utils.FileFolderPickerHelper;
import com.ymdelf.utils.ProcessExecutor;
import com.ymdelf.utils.WindowsRegistryUtil;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoize;

public class Main {
    private static String regPath;
    private static String regName;
    private static String regRoot;
    private static boolean antoChangeGitRemoteHEAD;
    private static boolean autoAddGitSafeDirectory;

    private static final String repoPath = System.getProperty("user.dir");
    //    private static final String repoPath = "D:\\temp\\test2\\矽杰微Git脚本测试";
    private static final String repoName = new File(Main.repoPath).getName();
    private static String remoteDir;
    private static String remoteGitPath;
    private static String gitVersionRaw;
    private static boolean shouldAddGitSafeDirectory = false;
    private static final Supplier<List<String>> safeDirsSupplier = memoize(() -> {
        if (!shouldAddGitSafeDirectory) return List.of();
        CommandResult res;
        try {
            res = ProcessExecutor.execResult(new CommandLine("git")
                    .addArgument("config")
                    .addArgument("--global")
                    .addArgument("--get-all")
                    .addArgument("safe.directory"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return res.exitCode == 0 ? res.out.lines().toList() : List.of();
    });

    public static void main(String[] args) throws Exception {
//========================== Step 01 Strat 读取配置 ==========================//
        System.out.println("Step 01: 加载配置");
        Main.readConfig();
        System.out.println(Main.antoChangeGitRemoteHEAD
                ? "已开启自动更改远程仓库 HEAD （本地仓库 HEAD 与 远程仓库 HEAD 不一致时生效）。"
                : "已关闭自动更改远程仓库 HEAD 。");
        System.out.println(Main.autoAddGitSafeDirectory
                ? "已开启自动添加安全目录。"
                : "已关闭自动添加安全目录（使用 SMB 协议与远程仓库连接时可能会因为 Security Identifier 不一致而被 ≥2.35.2 的 Git 拒绝）。");
        System.out.println("配置加载完成。");
        System.out.println();
//========================== Step 01 End 读取配置 ==========================//

//========================== Step 02 Strat 检查Git版本 ==========================//
        System.out.println("Step 02: 检查 Git 版本");
        // 获取Git版本Raw
        CommandResult gitVersionResult = ProcessExecutor.execResult(new CommandLine
                ("git")
                .addArgument("--version"));
        Main.shouldAddGitSafeDirectory = false;
        if (gitVersionResult.isSuccess()) {
            Main.gitVersionRaw = gitVersionResult.out;
            System.out.println(Main.gitVersionRaw);
            /*
              检测Git版本是否大于或等于2.35.2。
              为解决cve-2022-24765，
              git在2.35.2版本开始，新增了安全目录的配置。
              此程序执行时需要在安全目录下操作。
             */
            ComparableVersion current = new ComparableVersion(gitVersionRaw.substring("git version ".length()).split("\\s+")[0]);
            ComparableVersion baseline = new ComparableVersion("2.35.2");
            if (current.compareTo(baseline) >= 0) {
                Main.shouldAddGitSafeDirectory = true;
            }
        } else {
            System.err.println("检测不到 git ，请检查环境变量中 git 目录是否设置正确。");
            System.out.println();
            System.out.println("按 任意键 退出或直接关闭窗口...");
            CRT.INSTANCE._getch();
            System.exit(2);
        }
        System.out.println();
//========================== Step 02 End 检查Git版本 ==========================//

//========================== Step 03 Strat 检查Git仓库根路径 ==========================//
        System.out.println("Step 03: 检查 Git 仓库根路径");
        // 检测注册表是否存在
        if (WindowsRegistryUtil.valueExists(Main.regRoot, Main.regPath, Main.regName)) {
            Main.remoteDir = WindowsRegistryUtil.readString(Main.regRoot, Main.regPath, Main.regName);
            System.out.println("上次使用的路径为: " + Main.remoteDir);
            Scanner scanner = new Scanner(System.in);
            // 注册表中有历史路径，询问用户是否需要更改路径
            System.out.print("是否需要使用新的Git仓库根路径？（需要：输入 y 然后回车；不需要：直接回车）");
            String input = scanner.nextLine().trim();
            if (BooleanUtils.toBoolean(input)) {
                System.out.println("请在弹出的文件夹选择器中选择存放远程仓库的路径。");
                Main.remoteDir = chooseFolderOrExit();
                WindowsRegistryUtil.writeString(Main.regRoot, Main.regPath, Main.regName, Main.remoteDir);
            }
        } else {
            System.out.println("请在弹出的文件夹选择器中选择存放远程仓库的路径。");
            Main.remoteDir = chooseFolderOrExit();
            WindowsRegistryUtil.writeString(Main.regRoot, Main.regPath, Main.regName, Main.remoteDir);
        }
        System.out.println();
//========================== Step 03 End 检查Git仓库根路径 ==========================//

//========================== Step 04 Strat 初始化Git远程裸仓库 ==========================//
        System.out.println("Step 04: 初始化 Git 远程裸仓库");
        // 检测远程仓库的目录是否已存在
        Main.remoteGitPath = Main.remoteDir + "\\" + Main.repoName + ".git";
        if (Files.exists(Paths.get(Main.remoteGitPath))) {
            System.err.println("远程仓库文件夹 " + Main.remoteGitPath + " 已存在，将终止操作。");
            System.out.println("按 任意键 退出或直接关闭窗口...");
            CRT.INSTANCE._getch();
            System.exit(1);
        }

        // 初始化远程裸仓库
        CommandResult initRemoteRareRepositoryResult = ProcessExecutor.execResult(new CommandLine
                ("git")
                .addArgument("init")
                .addArgument("--bare")
                .addArgument(Main.remoteGitPath));
        if (StringUtils.isNotBlank(initRemoteRareRepositoryResult.out)) {
            System.out.println(initRemoteRareRepositoryResult.out);
        } else if (initRemoteRareRepositoryResult.isSuccess()) {
            System.out.println("完成");
        }
        System.out.println();
//========================== Step 04 End 初始化Git远程裸仓库 ==========================//

//========================== Step 05 Strat 检查HEAD分支 ==========================//
        System.out.println("Step 05: 检查 HEAD 指向");
        // 获取本地仓库的HEAD分支
        CommandResult localRefsHeadResult = ProcessExecutor.execResult(new CommandLine
                ("git")
                .addArgument("symbolic-ref")
                .addArgument("HEAD"));
        if (!localRefsHeadResult.isSuccess()) {
            System.err.println("获取本地 HEAD 时出现错误。");
            System.out.println(localRefsHeadResult.err);
            System.out.println("按 任意键 退出或直接关闭窗口...");
            CRT.INSTANCE._getch();
            System.exit(1);
        }
        String gitLocalRefsHead = localRefsHeadResult.out;
        String[] headLocalSplit = gitLocalRefsHead.split("/");
        String headLocalBranch = headLocalSplit[headLocalSplit.length - 1];
        System.out.println("本地仓库的 HEAD 指向：" + headLocalBranch);

        // 读取远程仓库的HEAD分支
        CommandLine queryRemoteHeadCommandLine = new CommandLine
                ("git")
                .addArgument("-C")
                .addArgument(Main.remoteGitPath)
                .addArgument("symbolic-ref")
                .addArgument("HEAD");
        CommandResult remoteRefsHeadResult = ProcessExecutor.execResult(queryRemoteHeadCommandLine);

        // 处理安全目录
        if (shouldAddGitSafeDirectory
                && remoteRefsHeadResult.exitCode == 128
                && remoteRefsHeadResult.err.startsWith("fatal: detected dubious ownership in repository at")) {
            System.out.println("读取远程仓库的 HEAD 指向时遇到了 dubious ownership 问题，正在添加安全目录...");
            List<String> safeDirs = safeDirsSupplier.get();
            String remoteGitPathUnix = FilenameUtils.separatorsToUnix(Main.remoteGitPath);
            if (safeDirs.stream().noneMatch(entry -> isPathCovered(entry, "%(prefix)/" + remoteGitPathUnix))) {
                // 未覆盖 → 追加
                CommandResult commandResult = ProcessExecutor.execResult(new CommandLine("git")
                        .addArgument("config")
                        .addArgument("--global")
                        .addArgument("--add")
                        .addArgument("safe.directory")
                        .addArgument("%(prefix)/" + remoteGitPathUnix));
                if (!commandResult.isSuccess()) {
                    System.err.println("添加安全目录失败。" + commandResult.err);
                    System.err.println(commandResult.err);
                    System.out.println("按 任意键 退出或直接关闭窗口...");
                    CRT.INSTANCE._getch();
                    System.exit(1);
                }
                if (StringUtils.isBlank(commandResult.out)) {
                    System.out.println("已添加安全目录：" + "%(prefix)/" + remoteGitPathUnix);
                } else {
                    System.out.println(commandResult.out);
                }
            } else {
                System.out.println("添加安全目录：" + "[已包含覆盖项，跳过添加]" + remoteGitPathUnix);
            }
            remoteRefsHeadResult = ProcessExecutor.execResult(queryRemoteHeadCommandLine);
        }
        if (!remoteRefsHeadResult.isSuccess()) {
            System.err.println("获取远程 HEAD 时出现错误。");
            System.out.println(remoteRefsHeadResult.err);
            System.out.println("按 任意键 退出或直接关闭窗口...");
            CRT.INSTANCE._getch();
            System.exit(1);
        }
        String gitRemoteRefsHead = remoteRefsHeadResult.out;
        String[] headRemoteSplit = gitRemoteRefsHead.split("/");
        String headRemoteBranch = headRemoteSplit[headRemoteSplit.length - 1];
        System.out.println("远程仓库的 HEAD 指向：" + headRemoteBranch);

        // 更改远程仓库的HEAD分支
        if (antoChangeGitRemoteHEAD) {
            CommandLine changeRemoteHeadCommandLine = new CommandLine
                    ("git")
                    .addArgument("-C")
                    .addArgument(Main.remoteGitPath)
                    .addArgument("symbolic-ref")
                    .addArgument("HEAD")
                    .addArgument(gitLocalRefsHead);
            CommandResult changeRemoteHead = ProcessExecutor.execResult(changeRemoteHeadCommandLine);

            if (!changeRemoteHead.isSuccess()) {
                System.err.println("更改远程 HEAD 时出现错误。");
                System.err.println(changeRemoteHead.err);
                System.out.println("按 任意键 退出或直接关闭窗口...");
                CRT.INSTANCE._getch();
                System.exit(1);
            }
            if (StringUtils.isNotBlank(changeRemoteHead.out)) {
                System.out.println(changeRemoteHead.out);
            }
            System.out.println("远程仓库的 HEAD 指向已从 " + headRemoteBranch + " 更改为 " + headLocalBranch);
        }
        System.out.println();
//========================== Step 05 End 检查HEAD分支 ==========================//

//========================== Step 06 Strat 为本地仓库添加远程仓库地址 ==========================//
        System.out.println("Step 06: 为本地仓库添加远程仓库地址");
        // 添加远程地址为 NAS
        CommandResult commandResult = ProcessExecutor.execResult(new CommandLine
                ("git")
                .addArgument("remote")
                .addArgument("add")
                .addArgument("NAS")
                .addArgument(Main.remoteGitPath));
        if (!commandResult.isSuccess()) {
            System.err.println("添加远程仓库地址时出现错误。");
            System.err.println(commandResult.err);
            System.out.println("按 任意键 退出或直接关闭窗口...");
            CRT.INSTANCE._getch();
            System.exit(1);
        }
        if (StringUtils.isBlank(commandResult.out)) {
            System.out.println("已添加：NAS = " + Main.remoteGitPath);
        } else {
            System.out.println(commandResult.out);
        }
        System.out.println();
//========================== Step 06 End 为本地仓库添加远程仓库地址 ==========================//

//========================== Step 07 Strat 推送 main 分支到远程 ==========================//
        System.out.println("Step 07: 推送本地 main 分支到远程");
        // 推送 main 分支到远程
        CommandLine pushCommandLine = new CommandLine
                ("git")
                .addArgument("push")
                .addArgument("-u")
                .addArgument("NAS")
                .addArgument(gitLocalRefsHead);
        CommandResult pushCommandResult = ProcessExecutor.execResult(pushCommandLine);

        // 处理安全目录
        if (shouldAddGitSafeDirectory
                && pushCommandResult.exitCode == 128
                && pushCommandResult.err.startsWith("fatal: detected dubious ownership in repository at")) {
            System.out.println("推送时遇到了 dubious ownership 问题，正在添加安全目录...");
            List<String> safeDirs = safeDirsSupplier.get();
            String remoteGitPathWindows = FilenameUtils.separatorsToWindows(Main.remoteGitPath);
            if (safeDirs.stream().noneMatch(entry -> isPathCovered(entry, remoteGitPathWindows))) {
                // 未覆盖 → 追加
                CommandResult addSafeDirectoryCommandResult = ProcessExecutor.execResult(new CommandLine("git")
                        .addArgument("config")
                        .addArgument("--global")
                        .addArgument("--add")
                        .addArgument("safe.directory")
                        .addArgument(remoteGitPathWindows));
                if (!commandResult.isSuccess()) {
                    System.err.println("添加安全目录失败。" + commandResult.err);
                    System.err.println(commandResult.err);
                    System.out.println("按 任意键 退出或直接关闭窗口...");
                    CRT.INSTANCE._getch();
                    System.exit(1);
                }
                if (StringUtils.isBlank(addSafeDirectoryCommandResult.out)) {
                    System.out.println("添加安全目录：" + remoteGitPathWindows);
                } else {
                    System.out.println(addSafeDirectoryCommandResult.out);
                }
            } else {
                System.out.println("添加安全目录：" + "[已包含覆盖项，跳过添加]" + remoteGitPathWindows);
            }
            pushCommandResult = ProcessExecutor.execResult(pushCommandLine);
        }
        if (!pushCommandResult.isSuccess()) {
            System.err.println("推送时出现错误。");
            System.err.println(pushCommandResult.err);
            System.out.println("按 任意键 退出或直接关闭窗口...");
            CRT.INSTANCE._getch();
            System.exit(1);
        }
        if (StringUtils.isNotBlank(pushCommandResult.out)) {
            System.out.println(pushCommandResult.out);
        }

        System.out.println("\n按 任意键 退出或直接关闭窗口...");
        CRT.INSTANCE._getch();
        System.exit(0);
//========================== Step 07 End 推送 main 分支到远程 ==========================//
    }


    private static void readConfig() {
        Properties properties = new Properties();

        // 从 resources 目录加载配置文件
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("配置文件未找到！");
                return;
            }
            properties.load(input);

            // 读取配置项
            Main.regPath = properties.getProperty("regPath");
            Main.regName = properties.getProperty("regName");
            Main.regRoot = properties.getProperty("regRoot");
            Main.antoChangeGitRemoteHEAD = BooleanUtils.toBoolean(properties.getProperty("antoChangeGitRemoteHEAD"));
            Main.autoAddGitSafeDirectory = BooleanUtils.toBoolean(properties.getProperty("autoAddGitSafeDirectory"));

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String chooseFolderOrExit() throws Exception {
        return FileFolderPickerHelper.pickFolder()
                .orElseGet(() -> {               // 用户取消 → 友好提示后退出
                    System.out.println("取消文件夹选择，程序即将退出。");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                    System.exit(1);
                    return null;                 // Unreachable，但编译器需要
                });
    }

    private static boolean isPathCovered(String entry, String path) {
        // 统一分隔符，去掉多余空白
        entry = entry.replace('\\', '/').trim();
        path = path.replace('\\', '/').trim();

        // 1) 全局通配
        if ("*".equals(entry)) {
            return true;
        }

        // 2) 通配符前缀 (xxx/*)
        if (entry.endsWith("*")) {
            String prefix = entry.substring(0, entry.length() - 1);
            return path.startsWith(prefix);
        }

        // 3) 完整父目录 / 同目录
        return path.equals(entry)                // 完全相同
                || path.startsWith(entry.endsWith("/") ? entry : entry + "/");
    }

    public interface CRT extends Library {
        CRT INSTANCE = Native.load("msvcrt", CRT.class);

        int _getch();
    }

}