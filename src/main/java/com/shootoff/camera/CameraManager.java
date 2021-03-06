/*
 * ShootOFF - Software for Laser Dry Fire Training
 * Copyright (C) 2015 phrack
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.shootoff.camera;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.webcam.Webcam;
import com.shootoff.config.Configuration;
import com.shootoff.gui.CanvasManager;
import com.shootoff.gui.ThresholdListener;
import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.ICloseEvent;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;

public class CameraManager {
	public static final int FEED_WIDTH = 640;
	public static final int FEED_HEIGHT = 480;
	
	private int bloomCount = 10;
	
	private final Logger logger = LoggerFactory.getLogger(CameraManager.class);
	private final Optional<Webcam> webcam;
	private final Object processingLock;
	private boolean processedVideo = false;
	private final CanvasManager canvasManager;
	private final Configuration config;
	private final int webcamRefreshDelay; // in milliseconds (ms)
	
	private boolean isStreaming = true;
	private boolean isDetecting = true;
	private Optional<Double> colorDiffThreshold = Optional.empty();
	private Optional<Integer> centerApproxBorderSize = Optional.empty();
	private Optional<Integer> minimumShotDimension = Optional.empty();
	private Optional<ThresholdListener> thresholdListener = Optional.empty();
	
	private boolean recording = false;
	private boolean isFirstFrame = true;
	private IMediaWriter videoWriter;
	private long recordingStartTime;
	private boolean[][] sectorStatuses;
	
	protected CameraManager(Webcam webcam, CanvasManager canvas, Configuration config) {
		this.webcam = Optional.of(webcam);
		processingLock = null;
		this.canvasManager = canvas;
		this.config = config;
		
		if (webcam.getFPS() == 0) {
			webcamRefreshDelay = 30; // ms
		} else {
			webcamRefreshDelay = (int)(1000 / webcam.getFPS());
		}
		
		init(new Detector());
	}
	
	protected CameraManager(File videoFile, Object processingLock, CanvasManager canvas, Configuration config) {
		this.webcam = Optional.empty();
		this.processingLock = processingLock;
		this.canvasManager = canvas;
		this.config = config;
		webcamRefreshDelay = 30;
		
		Detector detector = new Detector();
		
	    IMediaReader reader = ToolFactory.makeReader(videoFile.getAbsolutePath());
	    reader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
	    reader.addListener(detector);
	    
	    init(detector);
	    
	    while (reader.readPacket() == null)
	      do {} while(false);
	}
	
	private void init(Detector detector) {
		sectorStatuses = new boolean[ShotSearcher.SECTOR_ROWS][ShotSearcher.SECTOR_COLUMNS];
		
		// Turn on all shot sectors by default
		for (int x = 0; x < ShotSearcher.SECTOR_COLUMNS; x++) {
			for (int y = 0; y < ShotSearcher.SECTOR_ROWS; y++) {
				sectorStatuses[y][x] = true;
			}
		}
		
		new Thread(detector).start();
	}

	public boolean[][] getSectorStatuses() {
		return sectorStatuses;
	}
	
	public void setSectorStatuses(boolean[][] sectorStatuses) {
		this.sectorStatuses = sectorStatuses;
	}
	
	public void clearShots() {
		canvasManager.clearShots();
	}
	
	public void reset() {
		canvasManager.reset();
	}
	
	public void close() {
		if (webcam.isPresent()) webcam.get().close();
		if (recording) stopRecording();
	}
	
	public void setStreaming(boolean isStreaming) {
		this.isStreaming = isStreaming;
	}
	
	public void setDetecting(boolean isDetecting) {
		this.isDetecting = isDetecting;
	}
	
	public void startRecording(File videoFile) {
		logger.debug("Writing Video Feed To: {}", videoFile.getAbsoluteFile());
		videoWriter = ToolFactory.makeWriter(videoFile.getName());
		videoWriter.addVideoStream(0, 0, ICodec.ID.CODEC_ID_H264, FEED_WIDTH, FEED_HEIGHT);
		recordingStartTime = System.currentTimeMillis();
		isFirstFrame = true;
		
		recording = true;
	}
	
	public void stopRecording() {
		recording = false;
		videoWriter.close();
	}
	
	public Image getCurrentFrame() {
		if (webcam.isPresent()) {
			return SwingFXUtils.toFXImage(webcam.get().getImage(), null);
		} else {
			return null;
		}
	}
	
	public CanvasManager getCanvasManager() {
		return canvasManager;
	}
	
	public boolean getProcessedVideo() {
		return processedVideo;
	}
	
	public void setColorDiffThreshold(double threshold) {
		colorDiffThreshold = Optional.of(threshold);
		logger.debug("Set color component difference threshold: {}", threshold);
	}

	public void setCenterApproxBorderSize(int borderSize) {
		centerApproxBorderSize = Optional.of(borderSize);
		logger.debug("Set the shot center approximation border size to: {}", borderSize);
	}
	
	public void setMinimumShotDimension(int minDim) {
		minimumShotDimension = Optional.of(minDim);
		logger.debug("Set the minimum dimension for shots to: {}", minDim);
	}
	
	public void setBloomCount(int count) {
		bloomCount = count;
	}
	
	public void setThresholdListener(ThresholdListener thresholdListener) {
		this.thresholdListener = Optional.ofNullable(thresholdListener);
	}
	
	protected static BufferedImage threshold(Configuration config, BufferedImage grayScale) {
		BufferedImage threshholdedImg = new BufferedImage(grayScale.getWidth(),
				grayScale.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		
		for (int y = 0; y < grayScale.getHeight(); y++) {
			for (int x = 0; x < grayScale.getWidth(); x++) {
				int pixel = grayScale.getRGB(x, y) & 0xFF;
				
				if (pixel > config.getLaserIntensity()) {
					threshholdedImg.setRGB(x, y, mixColor(255, 255, 255));
				} else {
					threshholdedImg.setRGB(x, y, mixColor(0, 0, 0));
				}
			}
		}
		
		return threshholdedImg;
	}
	
	private static int mixColor(int red, int green, int blue) {
		return red << 16 | green << 8 | blue;
	}
	
	protected static byte[][] getFrameCount(BufferedImage img) {
		byte[][] newCount = new byte[FEED_HEIGHT][FEED_WIDTH];
		
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int pixel = img.getRGB(x, y) & 0xFF;
				if (pixel == 255) newCount[y][x] = 1;
			}
		}
		
		return newCount;
	}
	
	protected BufferedImage countToImage(byte[][] count) {
		BufferedImage img = new BufferedImage(count[0].length,
				count.length, BufferedImage.TYPE_BYTE_GRAY);
		
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if (count[y][x] == 1) {
					img.setRGB(x, y, mixColor(255, 255, 255));
				} else {
					img.setRGB(x, y, mixColor(0, 0, 0));
				}
			}
		}
		
		return img;
	}
	
	private class Detector extends MediaListenerAdapter implements Runnable {
		private BufferedImage currentFrame;
		private int oldestFrame = 0;
		private List<Object> counts = new ArrayList<Object>();
		private byte[][] bloomFilter = new byte[FEED_HEIGHT][FEED_WIDTH];
		private boolean bloomFilterInitialized = false;
		
		@Override
		public void run() {
			if (webcam.isPresent()) {
				if (!webcam.get().isOpen()) {
					webcam.get().setViewSize(new Dimension(FEED_WIDTH, FEED_HEIGHT));
					webcam.get().open();			
				}
				
				streamCameraFrames();
			}
		}
		
		@Override
		public void onVideoPicture(IVideoPictureEvent event)
		{
			currentFrame = event.getImage();
			detectShots();
		}
		
		@Override
		public void onClose(ICloseEvent event) {
			synchronized (processingLock) {
				processedVideo = true;
				processingLock.notifyAll();
			}
		}
		
		private void streamCameraFrames() {
			long startDetectionCycle = System.currentTimeMillis();
			
			while (isStreaming) {
				if (webcam.isPresent()) {
					currentFrame = webcam.get().getImage();
				}
				
				if (currentFrame == null && webcam.isPresent() && !webcam.get().isOpen()) {
					Platform.runLater(() -> {
							Alert cameraAlert = new Alert(AlertType.ERROR);
							
							Optional<String> cameraName = config.getWebcamsUserName(webcam.get());
							String messageFormat = "ShootOFF can no longer communicate with the webcam %s. Was it unplugged?";
							String message;
							if (cameraName.isPresent()) {
								message = String.format(messageFormat, cameraName.get());
							} else {
								message = String.format(messageFormat, webcam.get().getName());
							}
							
							cameraAlert.setTitle("Webcam Missing");
							cameraAlert.setHeaderText("Cannot Communicate with Camera!");
							cameraAlert.setResizable(true);
							cameraAlert.setContentText(message);
							cameraAlert.show();
						});
					
					return;
				}
				
				if (recording) {
					BufferedImage image = ConverterFactory.convertToType(currentFrame, BufferedImage.TYPE_3BYTE_BGR);
					IConverter converter = ConverterFactory.createConverter(image, IPixelFormat.Type.YUV420P);

					IVideoPicture frame = converter.toPicture(image, 
							(System.currentTimeMillis() - recordingStartTime) * 1000);
					frame.setKeyFrame(isFirstFrame);
					frame.setQuality(0);
					isFirstFrame = false;

					videoWriter.encodeVideo(0, frame);
				}
				
				Image img = SwingFXUtils.toFXImage(currentFrame, null);
				canvasManager.updateBackground(img);
				
				if (System.currentTimeMillis() - 
						startDetectionCycle >= config.getDetectionRate()) {
					
					startDetectionCycle = System.currentTimeMillis();
					detectShots();
				}
				
				try {
					Thread.sleep(webcamRefreshDelay);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		private void addFrameCount(byte[][] count) {
			for (int y = 0; y < FEED_HEIGHT; y++) {
				for (int x = 0; x < FEED_WIDTH; x++) {
					bloomFilter[y][x] += count[y][x];
				}
			}
		}
		
		private void subFrameCount(byte[][] count) {
			for (int y = 0; y < FEED_HEIGHT; y++) {
				for (int x = 0; x < FEED_WIDTH; x++) {
					if (bloomFilter[y][x] >= 1)
						bloomFilter[y][x] -= count[y][x];
				}
			}
		}
		
		private byte[][] generateMask() {
			byte[][] mask = new byte[FEED_HEIGHT][FEED_WIDTH];
			
			for (int y = 0; y < FEED_HEIGHT; y++) {
				for (int x = 0; x < FEED_WIDTH; x++) {
					if (bloomFilter[y][x] == 0) mask[y][x] = 0;
					else mask[y][x] = 1;
				}
			}
			
			return mask;	
		}
		
		private byte not(byte value) {
			if (value == 1) return 0;
			else return 1;
		}
		
		private byte[][] getShotFrame(byte[][] mask, byte[][] currentFrame) {
			byte[][] shotFrame = new byte[FEED_HEIGHT][FEED_WIDTH];
			
			for (int y = 0; y < FEED_HEIGHT; y++) {
				for (int x = 0; x < FEED_WIDTH; x++) {
					shotFrame[y][x] = (byte) (not(mask[y][x]) & currentFrame[y][x]);
				}
			}
			
			return shotFrame;	
		}
		
		private void detectShots() {
			if (!isDetecting || bloomCount == 0) return;
			
			BufferedImage currentCopy = new BufferedImage(currentFrame.getWidth(),
					currentFrame.getHeight(), BufferedImage.TYPE_INT_RGB);
			currentCopy.createGraphics().drawImage(currentFrame, 0, 0, null);
			
			BufferedImage grayScale = new BufferedImage(currentCopy.getWidth(),
					currentFrame.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			grayScale.createGraphics().drawImage(currentCopy, 0, 0, null);
			
			BufferedImage threshed = threshold(config, grayScale);
			
			// This condition is true if the stream debugger is opened
			// and the bloom count slider is changed
			if (bloomFilterInitialized && counts.size() != bloomCount) {
				counts.clear();
				bloomFilterInitialized = false;
				oldestFrame = 0;
			}
			
			if (bloomFilterInitialized) {
				byte[][] currentFrame = getFrameCount(threshed);
				byte[][] shotFrame = getShotFrame(generateMask(), currentFrame);
				
				ShotSearcher shotSearcher = new ShotSearcher(config, canvasManager, sectorStatuses,
						currentCopy, shotFrame);
				
				if (colorDiffThreshold.isPresent()) {
					shotSearcher.setColorDiffThreshold(colorDiffThreshold.get());
				}
				
				if (centerApproxBorderSize.isPresent()) {
					shotSearcher.setCenterApproxBorderSize(centerApproxBorderSize.get());
				}
				
				if (minimumShotDimension.isPresent()) {
					shotSearcher.setMinimumShotDimension(minimumShotDimension.get());
				}
				
				new Thread(shotSearcher).start();
				
				if (thresholdListener.isPresent()) {
					Image img = SwingFXUtils.toFXImage(countToImage(shotFrame), null);
					thresholdListener.get().updateThreshold(img);
				}
				
				// Update the bloom filter by removing the oldest frame
				// and adding the current one
				subFrameCount((byte[][])counts.get(oldestFrame));
				counts.set(oldestFrame, currentFrame);
				addFrameCount(currentFrame);
				oldestFrame = (oldestFrame + 1) % bloomCount;
			} else {
				if (counts.size() != bloomCount) counts.add(getFrameCount(threshed));
				
				if (counts.size() == bloomCount) {
					bloomFilterInitialized = true;
					logger.debug("Finished initializing bloom filter ({} frames in filter): Enabling Shot Detection",
							bloomCount);
					for (Object count : counts) addFrameCount((byte[][])count);
				}
			}
		}
	}	
}