package com.aplus.remotenursing.common;


import java.io.*;
import java.security.MessageDigest;
import okhttp3.*;
import com.aplus.remotenursing.helper.ApiClientHelper;

public class FileDownloadUtil {

    public static boolean downloadTo(String url, File tmp) {
        try {
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = ApiClientHelper.get().newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return false;
                try (InputStream is = resp.body().byteStream();
                     OutputStream os = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
                }
            }
            return true;
        } catch (Exception e) { return false; }
    }

    public static String md5(File f) {
        try (InputStream is = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public static boolean atomicReplace(File srcTmp, File dst) {
        try {
            if (dst.exists()) dst.delete();
            if (srcTmp.renameTo(dst)) return true;
            try (InputStream in = new FileInputStream(srcTmp);
                 OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            //noinspection ResultOfMethodCallIgnored
            srcTmp.delete();
            return true;
        } catch (Exception e) { return false; }
    }
}
