package org.linlinjava.litemall.db.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class DbUtil {

    public static void backup(File file, String user, String password, String db) {
        Process child = null;
        List<String> command = Arrays.asList("mysqldump", "-u" + user, "--set-charset=utf8", db);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (password != null) {
                builder.environment().put("MYSQL_PWD", password);
            }
            child = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
                 OutputStreamWriter writer = new OutputStreamWriter(
                         new FileOutputStream(file), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\r\n");
                }
                writer.flush();
            }
            child.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (child != null) {
                child.destroy();
            }
        }
    }

    public static void load(File file, String user, String password, String db) {
        Process child = null;
        List<String> command = Arrays.asList("mysql", "-u" + user, "--default-character-set=utf8", db);
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (password != null) {
                builder.environment().put("MYSQL_PWD", password);
            }
            child = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                 OutputStreamWriter writer = new OutputStreamWriter(
                         child.getOutputStream(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\r\n");
                }
                writer.flush();
            }
            child.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (child != null) {
                child.destroy();
            }
        }
    }
}
