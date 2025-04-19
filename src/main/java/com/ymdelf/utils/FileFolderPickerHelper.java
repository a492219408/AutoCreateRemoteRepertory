package com.ymdelf.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class FileFolderPickerHelper {

    private static final String EXE_RESOURCE = "bin/FileFolderPicker.exe";
    private static final Path PICKER_EXE = extractPickerExe();

    /** 提取 exe 到临时目录（只做一次） */
    private static Path extractPickerExe() {
        try (InputStream in = FileFolderPickerHelper.class
                .getClassLoader().getResourceAsStream(EXE_RESOURCE)) {

            if (in == null) {
                throw new FileNotFoundException("缺少资源：" + EXE_RESOURCE);
            }

            Path tmp = Files.createTempFile("picker_", ".exe");
            tmp.toFile().deleteOnExit();
            try (OutputStream out = Files.newOutputStream(tmp)) {
                in.transferTo(out);
            }
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException("提取 FileFolderPicker 失败", e);
        }
    }

    /**
     * 弹出文件夹选择器。
     * @return Optional.empty() 代表用户点了“取消”
     */
    public static Optional<String> pickFolder() throws Exception {
        CommandResult result = ProcessExecutor.execResult(
                PICKER_EXE.toString(), "--folder"
        );

        if (result.isSuccess()) {
            return Optional.of(result.out.trim());
        }
        if (result.exitCode == 1) {          // 用户取消
            return Optional.empty();
        }
        throw new IllegalStateException(
                "FileFolderPicker 异常退出，exitCode = " + result.exitCode);
    }

    private FileFolderPickerHelper() {}     // 工具类禁止实例化
}
