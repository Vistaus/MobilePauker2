package com.daniel.mobilepauker2.dropbox;

import android.os.AsyncTask;

import com.daniel.mobilepauker2.utils.Constants;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Async task to upload a file to a directory
 */
public class UploadFileTask extends AsyncTask<File, Void, List<Metadata>> {

    private final DbxClientV2 mDbxClient;
    private final Callback mCallback;
    private Exception mException;

    public interface Callback {
        void onUploadComplete(List<Metadata> result);
        void onError(Exception e);
    }

    UploadFileTask(DbxClientV2 dbxClient, Callback callback) {
        mDbxClient = dbxClient;
        mCallback = callback;
    }

    @Override
    protected void onPostExecute(List<Metadata> result) {
        super.onPostExecute(result);
        if (mException != null) {
            mCallback.onError(mException);
        } else if (result == null) {
            mCallback.onError(null);
        } else {
            mCallback.onUploadComplete(result);
        }
    }

    @Override
    protected List<Metadata> doInBackground(File... params) {
        File remoteFolderPath = new File(Constants.DROPBOX_PATH);
        List<Metadata> data = new ArrayList<>();

        for (File localFile : params) {
            if (localFile != null) {
                try (InputStream inputStream = new FileInputStream(localFile)) {
                    // Note - this is not ensuring the name is a valid dropbox file name
                    String remoteFileName = localFile.getName();
                    data.add(mDbxClient.files().uploadBuilder(remoteFolderPath + "/" + remoteFileName)
                            .withMode(WriteMode.OVERWRITE)
                            .uploadAndFinish(inputStream));
                } catch (DbxException | IOException e) {
                    mException = e;
                }
            }
        }

        return data;
    }
}