package bundle.download;

public interface ProgressCallback {
    void onProgress(long bytesDownloaded, long totalBytes, double downloadSpeed, String fileName);
    void onDownloadComplete(String fileName);
    void onDownloadStart(String fileName, long totalBytes);
}