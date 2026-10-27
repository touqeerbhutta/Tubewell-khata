package com.touqeer.tubewellkhata;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Khata ki tasveer ko gallery (Pictures/TubewellKhata) mein save karta ha.
 * JS se: Capacitor.Plugins.GallerySaver.save({ base64: '...', name: 'Khata_Ali_2026-10-27' })
 * Android 10+ : MediaStore (koi permission nahi chahiye). Android 9 aur purana: WRITE_EXTERNAL_STORAGE.
 */
@CapacitorPlugin(
    name = "GallerySaver",
    permissions = {
        @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "storage")
    }
)
public class GallerySaverPlugin extends Plugin {

    private static final String FOLDER = "TubewellKhata";

    @PluginMethod
    public void save(PluginCall call) {
        if (Build.VERSION.SDK_INT < 29 && getPermissionState("storage") != PermissionState.GRANTED) {
            requestPermissionForAlias("storage", call, "storageCallback");
            return;
        }
        doSave(call);
    }

    @PermissionCallback
    private void storageCallback(PluginCall call) {
        if (getPermissionState("storage") == PermissionState.GRANTED) {
            doSave(call);
        } else {
            call.reject("Storage ki ijazat nahi mili");
        }
    }

    private void doSave(PluginCall call) {
        String b64 = call.getString("base64");
        String name = call.getString("name");
        if (b64 == null || name == null || name.isEmpty()) {
            call.reject("base64 aur name zaroori hain");
            return;
        }
        try {
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            String fileName = name + ".png";
            JSObject result = new JSObject();

            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = getContext().getContentResolver();

                // Aaj ki purani same-naam tasveer hata do, taake ek hi din ki duplicate na banen
                try {
                    cr.delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Images.Media.DISPLAY_NAME + "=? AND " + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?",
                        new String[] { fileName, Environment.DIRECTORY_PICTURES + "/" + FOLDER + "%" }
                    );
                } catch (Exception ignored) {
                    // delete na ho to bhi naya save chalay
                }

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER);
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    call.reject("Gallery mein file nahi ban saki");
                    return;
                }
                OutputStream os = cr.openOutputStream(uri);
                if (os == null) {
                    call.reject("Gallery file khul nahi saki");
                    return;
                }
                try {
                    os.write(bytes);
                    os.flush();
                } finally {
                    os.close();
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, values, null, null);
                result.put("uri", uri.toString());
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER);
                if (!dir.exists() && !dir.mkdirs()) {
                    call.reject("Folder nahi ban saka");
                    return;
                }
                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    fos.write(bytes);
                    fos.flush();
                } finally {
                    fos.close();
                }
                MediaScannerConnection.scanFile(
                    getContext(),
                    new String[] { file.getAbsolutePath() },
                    new String[] { "image/png" },
                    null
                );
                result.put("uri", file.getAbsolutePath());
            }
            call.resolve(result);
        } catch (Exception e) {
            call.reject("Save nahi hua: " + e.getMessage());
        }
    }
}
