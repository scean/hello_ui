package com.android.providers.downloads.ui.notification;

/**
 * 此处写类描述
 * @Package com.android.providers.downloads.ui.notification
 * @ClassName: Util
 * @author gaogb
 * @mail gaoguibao@xunlei.com
 * @date 2014年6月20日 下午2:57:13
 */
public class Util {
    private static final int BASE_B = 1; // 转换为字节基数
    private static final int BASE_KB = 1024; // 转换为KB
    private static final int BASE_MB = 1024 * 1024; // 转换为M的基数
    private static final int BASE_GB = 1024 * 1024 * 1024;

    private static final String UNIT_BIT = "KB";
    private static final String UNIT_KB = "KB";
    private static final String UNIT_MB = "M";
    private static final String UNIT_GB = "G";

    public static String convertFileSize(long file_size, int precision) {
        long int_part = 0;
        double fileSize = file_size;
        double floatSize = 0L;
        long temp = file_size;
        int i = 0;
        int base = 1;
        String baseUnit = "";
        String fileSizeStr = null;
        int indexMid = 0;

        while (temp / 1024 > 0) {
            int_part = temp / 1024;
            temp = int_part;
            i++;
        }
        switch (i) {
        case 0:
            // B
            base = BASE_B;
            baseUnit = UNIT_BIT;
            break;

        case 1:
            // KB
            base = BASE_KB;
            baseUnit = UNIT_KB;
            break;

        case 2:
            // MB
            base = BASE_MB;
            baseUnit = UNIT_MB;
            break;

        case 3:
            // GB
            base = BASE_GB;
            baseUnit = UNIT_GB;
            break;

        case 4:
            // TB
            break;
        default:
            break;
        }
        floatSize = fileSize / base;
        fileSizeStr = Double.toString(floatSize);
        if (precision == 0) {
            indexMid = fileSizeStr.indexOf('.');
            if (-1 == indexMid) {
                // 字符串中没有这样的字符
                return fileSizeStr + baseUnit;
            }
            return fileSizeStr.substring(0, indexMid) + baseUnit;
        }
        indexMid = fileSizeStr.indexOf('.');
        if (-1 == indexMid) {
            // 字符串中没有这样的字符
            return fileSizeStr + baseUnit;
        }

        if (fileSizeStr.length() <= indexMid + precision + 1) {
            return fileSizeStr + baseUnit;
        }
        if (indexMid < 3) {
            indexMid += 1;
        }
        if (indexMid + precision < 6) {
            indexMid = indexMid + precision;
        }
        return fileSizeStr.substring(0, indexMid) + baseUnit;
    }

}
