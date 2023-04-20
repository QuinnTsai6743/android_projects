package com.graystone.camera03;

import android.provider.MediaStore;

import androidx.annotation.NonNull;

public interface ISaveFile {
    enum FileType {
        RAW("image/x-panasonic-raw", "raw", MediaStore.Images.Media.DATA),
        JPEG("image/jpeg", "jpg", MediaStore.Images.Media.DATA),
        PNG("image/png", "png", MediaStore.Images.Media.DATA),
        BMP("image/bmp", "bmp", MediaStore.Images.Media.DATA),
        TXT("text/plain", "txt", MediaStore.Files.FileColumns.DATA);

        private final String mMimeType;
        private final String mExt;
        private final String mDataColumnName;

        FileType(String mimeType, String ext, String dataColumnName) {
            mMimeType = mimeType;
            mExt = ext;
            mDataColumnName = dataColumnName;
        }

        public String mimeType() { return mMimeType; }
        public String fileExt() { return mExt; }
        public String dataColumnName() { return mDataColumnName; }
    }

    void write(String title, FileType fileType, @NonNull IWriteToStream writeToStream);
    void write(String title, FileType fileType, byte[] data);
}
