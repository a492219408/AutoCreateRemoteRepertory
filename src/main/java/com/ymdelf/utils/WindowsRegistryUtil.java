package com.ymdelf.utils;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

public class WindowsRegistryUtil {
    public WindowsRegistryUtil() {}

    public enum RootKey {
        HKCU, HKEY_CURRENT_USER,
        HKLM, HKEY_LOCAL_MACHINE
    }

    private static WinReg.HKEY resolveRootKey(String rootKey) {
        switch (rootKey.toUpperCase()) {
            case "HKCU":
            case "HKEY_CURRENT_USER":
                return WinReg.HKEY_CURRENT_USER;
            case "HKLM":
            case "HKEY_LOCAL_MACHINE":
                return WinReg.HKEY_LOCAL_MACHINE;
            default:
                throw new IllegalArgumentException("Unsupported root key: " + rootKey);
        }
    }

    // 读取字符串值
    public static String readString(String root, String keyPath, String valueName) {
        WinReg.HKEY hkey = resolveRootKey(root);
        return Advapi32Util.registryGetStringValue(hkey, keyPath, valueName);
    }

    // 写入字符串值
    public static void writeString(String root, String keyPath, String valueName, String value) {
        WinReg.HKEY hkey = resolveRootKey(root);
        Advapi32Util.registryCreateKey(hkey, keyPath); // 若不存在则创建
        Advapi32Util.registrySetStringValue(hkey, keyPath, valueName, value);
    }

    // 检查键值是否存在
    public static boolean valueExists(String root, String keyPath, String valueName) {
        WinReg.HKEY hkey = resolveRootKey(root);
        return Advapi32Util.registryValueExists(hkey, keyPath, valueName);
    }

    // 删除键值（可选）
    public static void deleteValue(String root, String keyPath, String valueName) {
        WinReg.HKEY hkey = resolveRootKey(root);
        if (valueExists(root, keyPath, valueName)) {
            Advapi32Util.registryDeleteValue(hkey, keyPath, valueName);
        }
    }

    // 删除整个键（包括所有子键）
    public static void deleteKey(String root, String keyPath) {
        WinReg.HKEY hkey = resolveRootKey(root);
        if (Advapi32Util.registryKeyExists(hkey, keyPath)) {
            Advapi32Util.registryDeleteKey(hkey, keyPath);
        }
    }
}
