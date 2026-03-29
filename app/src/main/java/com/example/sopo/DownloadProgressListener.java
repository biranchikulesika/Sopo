package com.example.sopo;

import com.chaquo.python.PyObject;

public interface DownloadProgressListener {
    void onProgress(PyObject data);
}
