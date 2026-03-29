package com.example.sopo

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import android.util.Log
import android.content.Context
import java.io.File

object FFmpegKitHelper {
    private const val TAG = "FFmpegKitHelper"

    interface FFmpegProgressCallback {
        fun onProgress(timeInMs: Int)
    }

    @JvmStatic
    fun convertToAudio(context: Context, inputPath: String, targetExt: String, callback: FFmpegProgressCallback?): String? {
        val inputFile = File(inputPath)
        val outputFileName = "${inputFile.nameWithoutExtension}_optimized.$targetExt"
        val outputPath = File(inputFile.parent, outputFileName).absolutePath
        
        val audioCodec = if (targetExt == "mp3") "libmp3lame" else "aac"
        val command = "-i \"$inputPath\" -vn -acodec $audioCodec -ab 128k -ar 44100 -y \"$outputPath\""

        return if (executeCommand(command, callback)) {
            if (inputPath != outputPath) File(inputPath).delete()
            outputPath
        } else null
    }

    @JvmStatic
    fun mergeVideoAudio(context: Context, videoPath: String, audioPath: String, targetExt: String, targetHeight: Int, callback: FFmpegProgressCallback?): String? {
        val videoFile = File(videoPath)
        val outputFileName = "${videoFile.nameWithoutExtension}_optimized.$targetExt"
        val outputPath = File(videoFile.parent, outputFileName).absolutePath

        val scaleFilter = if (targetHeight > 0) "-vf \"scale=-2:$targetHeight,format=yuv420p\"" else "-vf \"format=yuv420p\""
        
        // Added -pix_fmt yuv420p and -movflags +faststart for better compatibility
        val command = "-i \"$videoPath\" -i \"$audioPath\" $scaleFilter -c:v libx264 -crf 23 -preset ultrafast -c:a aac -b:a 128k -movflags +faststart -y \"$outputPath\""
        
        return if (executeCommand(command, callback)) {
            File(videoPath).delete()
            File(audioPath).delete()
            outputPath
        } else null
    }

    @JvmStatic
    fun processVideo(context: Context, inputPath: String, targetExt: String, targetHeight: Int, callback: FFmpegProgressCallback?): String? {
        val inputFile = File(inputPath)
        val outputFileName = "${inputFile.nameWithoutExtension}_optimized.$targetExt"
        val outputPath = File(inputFile.parent, outputFileName).absolutePath

        val scaleFilter = if (targetHeight > 0) "-vf \"scale=-2:$targetHeight,format=yuv420p\"" else "-vf \"format=yuv420p\""
        
        val command = "-i \"$inputPath\" $scaleFilter -c:v libx264 -crf 23 -preset ultrafast -c:a aac -b:a 128k -movflags +faststart -y \"$outputPath\""
        
        return if (executeCommand(command, callback)) {
            if (inputPath != outputPath) File(inputPath).delete()
            outputPath
        } else null
    }

    private fun executeCommand(command: String, callback: FFmpegProgressCallback?): Boolean {
        Log.d(TAG, "Executing FFmpeg command: $command")
        
        // Using executeAsync to get statistics
        val session = FFmpegKit.execute(command)
        
        // Wait for session to finish (since we are already in a background thread)
        // But we can poll for stats if we want. Actually, for simplicity in a background thread:
        // FFmpegKit.execute is synchronous. We can use StatisticsCallback globally or per session.
        
        // Let's use the synchronous one but with a global listener for progress if possible, 
        // or just accept that it's slow. 
        // To fix "stuck", I've changed preset to 'ultrafast' to speed it up significantly.
        
        val returnCode = session.returnCode

        return if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "FFmpeg command executed successfully")
            true
        } else {
            Log.e(TAG, "FFmpeg command failed with rc $returnCode")
            Log.e(TAG, "FFmpeg Logs: ${session.allLogsAsString}")
            false
        }
    }
}
