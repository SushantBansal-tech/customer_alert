package com.alertsystem.service;

import com.alertsystem.dto.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class LogReaderService {
    
    private static final Logger logger = LoggerFactory.getLogger(LogReaderService.class);
    
    @Autowired
    private ActivityCaptureService activityCaptureService;
    
    @Value("${app.log.path:logs/app.log}")
    private String logPath;
    
    private RandomAccessFile logFile;
    private long lastKnownPosition = 0;
    private final ConcurrentLinkedQueue<String> logBuffer = new ConcurrentLinkedQueue<>();
    
    @PostConstruct
    public void init() {
        try {
            logFile = new RandomAccessFile(logPath, "r");
            lastKnownPosition = logFile.length(); // Start from end
            logger.info("LogReaderService initialized with log file: {}", logPath);
        } catch (IOException e) {
            logger.error("Error initializing log file: {}", logPath, e);
        }
    }
    
    // Run every 10 seconds to read new logs
    @Scheduled(fixedRateString = "${app.log.read.interval:10000}")
    public void readNewLogs() {
        if (logFile == null) {
            return;
        }
        
        try {
            logFile.seek(lastKnownPosition);
            String line;
            
            while ((line = logFile.readLine()) != null) {
                logBuffer.offer(line);
                processLogLine(line);
            }
            
            lastKnownPosition = logFile.getFilePointer();
            
        } catch (IOException e) {
            logger.error("Error reading log file", e);
        }
    }
    
    private void processLogLine(String line) {
        // Only process ERROR logs with REQUEST_END
        if (line != null && line.contains("ERROR") && line.contains("[REQUEST_END]") && 
            line.contains("[STATUS:FAILED]")) {
            
            LogEvent event = LogParser.parseFailureLog(line);
            if (event != null && event.getUserId() != null) {
                activityCaptureService.captureActivity(event);
            }
        }
    }
    
    public int getBufferSize() {
        return logBuffer.size();
    }
}