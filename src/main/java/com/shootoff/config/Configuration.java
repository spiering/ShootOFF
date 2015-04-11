/*
 * Copyright (c) 2015 phrack. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.shootoff.config;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Configuration {
	private static final String DETECTION_RATE_PROP = "shootoff.detectionrate";
	private static final String LASER_INTENSITY_PROP = "shootoff.laserintensity";
	private static final String MARKER_RADIUS_PROP = "shootoff.markerradius";
	private static final String IGNORE_LASER_COLOR_PROP = "shootoff.ignorelasercolor";
	private static final String USE_VIRTUAL_MAGAZINE_PROP = "shootoff.virtualmagazine.use";
	private static final String VIRTUAL_MAGAZINE_CAPACITY_PROP = "shootoff.virtualmagazine.capacity";
	private static final String USE_MALFUNCTIONS_PROP = "shootoff.malfunctions.use";
	private static final String MALFUNCTIONS_PROBABILITY_PROP = "shootoff.malfunctions.probability";
	
	protected static final String DETECTION_RATE_MESSAGE = 
			"DETECTION_RATE has an invalid value: %d. Acceptable values are "
			+ "greater than 0.";
	protected static final String LASER_INTENSITY_MESSAGE = 
			"LASER_INTENSITY has an invalid value: %d. Acceptable values are "
			+ "between 1 and 255.";
	protected static final String MARKER_RADIUS_MESSAGE = 
			"MARKER_RADIUS has an invalid value: %d. Acceptable values are "
			+ "between 1 and 20.";
	protected static final String LASER_COLOR_MESSAGE = 
			"LASER_COLOR has an invalid value: %s. Acceptable values are "
			+ "\"red\" and \"green\".";
	protected static final String VIRTUAL_MAGAZINE_MESSAGE = 
			"VIRTUAL_MAGAZINE has an invalid value: %d. Acceptable values are "
			+ "between 1 and 45.";
	protected static final String INJECT_MALFUNCTIONS_MESSAGE = 
			"INJECT_MALFUNCTIONS has an invalid value: %f. Acceptable values are "
			+ "between 0.1 and 99.9.";
	
	private static final String DEFAULT_CONFIG_FILE = "shootoff.properties";

	private InputStream configInput;
	private String configName;
	
	private int detectionRate = 100;
	private int laserIntensity = 230;
	private int markerRadius = 2;
	private boolean ignoreLaserColor = false;
	private String ignoreLaserColorName = "None";
	private boolean useVirtualMagazine = false;
	private int virtualMagazineCapacity = 7;
	private boolean useMalfunctions = false;
	private float malfunctionsProbability = (float)10.0;
	private boolean debugMode = false;

	protected Configuration(InputStream configInputStream, String name) throws IOException, ConfigurationException {
		configInput = configInputStream;
		configName = name;
		readConfigurationFile();
	}
	
	public Configuration(String name) throws IOException, ConfigurationException {
		configName = name;
		readConfigurationFile();
	}
	
	protected Configuration(InputStream configInputStream, String name, String[] args) throws IOException, ConfigurationException {
		configInput = configInputStream;
		configName = name;
		readConfigurationFile();
		parseCmdLine(args);
	}
	
	public Configuration(String name, String[] args) throws IOException, ConfigurationException {
		configName = name;
		readConfigurationFile();
		parseCmdLine(args);
	}

	public Configuration(String[] args) throws ConfigurationException {
		configName = DEFAULT_CONFIG_FILE;
		parseCmdLine(args);
	}
	
	private void readConfigurationFile() throws IOException, ConfigurationException {
		Properties prop = new Properties();
		
		InputStream inputStream;
		
		if (configInput != null) {
			inputStream = configInput;
		} else {
			inputStream = getClass().getClassLoader().getResourceAsStream(configName);
		}
			 
		if (inputStream != null) {
			prop.load(inputStream);
		} else {
			throw new FileNotFoundException("Could not read configuration file " +
					configName);
		}
		
		if (prop.containsKey(DETECTION_RATE_PROP)) {
			detectionRate = Integer.parseInt(prop.getProperty(DETECTION_RATE_PROP));
		}
		
		if (prop.containsKey(LASER_INTENSITY_PROP)) {
			laserIntensity = Integer.parseInt(prop.getProperty(LASER_INTENSITY_PROP));
		}
		
		if (prop.containsKey(MARKER_RADIUS_PROP)) {
			markerRadius = Integer.parseInt(prop.getProperty(MARKER_RADIUS_PROP));
		}
		
		if (prop.containsKey(IGNORE_LASER_COLOR_PROP)) {
			String colorName = prop.getProperty(IGNORE_LASER_COLOR_PROP);
			
			if (!colorName.equals("None")) {
				ignoreLaserColor = true;
				ignoreLaserColorName = colorName;
			} 
		}
		
		if (prop.containsKey(USE_VIRTUAL_MAGAZINE_PROP)) {
			useVirtualMagazine = Boolean.parseBoolean(prop.getProperty(USE_VIRTUAL_MAGAZINE_PROP));
		}
		
		if (prop.containsKey(VIRTUAL_MAGAZINE_CAPACITY_PROP)) {
			virtualMagazineCapacity = Integer.parseInt(prop.getProperty(VIRTUAL_MAGAZINE_CAPACITY_PROP));
		}
		
		if (prop.containsKey(USE_MALFUNCTIONS_PROP)) {
			useMalfunctions = Boolean.parseBoolean(prop.getProperty(USE_MALFUNCTIONS_PROP));
		}
		
		if (prop.containsKey(MALFUNCTIONS_PROBABILITY_PROP)) {
			malfunctionsProbability = Float.parseFloat(prop.getProperty(MALFUNCTIONS_PROBABILITY_PROP));
		}
		
		validateConfiguration();
	}
	
	public void writeConfigurationFile() throws ConfigurationException, IOException {
		validateConfiguration();
		
		Properties prop = new Properties();
		
		prop.setProperty(DETECTION_RATE_PROP, String.valueOf(detectionRate));
		prop.setProperty(LASER_INTENSITY_PROP, String.valueOf(laserIntensity));
		prop.setProperty(MARKER_RADIUS_PROP, String.valueOf(markerRadius));
		prop.setProperty(IGNORE_LASER_COLOR_PROP, ignoreLaserColorName);
		prop.setProperty(USE_VIRTUAL_MAGAZINE_PROP, String.valueOf(useVirtualMagazine));
		prop.setProperty(VIRTUAL_MAGAZINE_CAPACITY_PROP, String.valueOf(virtualMagazineCapacity));
		prop.setProperty(USE_MALFUNCTIONS_PROP, String.valueOf(useMalfunctions));
		prop.setProperty(MALFUNCTIONS_PROBABILITY_PROP, String.valueOf(malfunctionsProbability));
		
		OutputStream outputStream = new FileOutputStream(configName);
		prop.store(outputStream, "ShootOFF Configuration");
	}
	
	private void parseCmdLine(String[] args) throws ConfigurationException {
		Options options = new Options();

		options.addOption("d", "debug", false, "turn on debug log messages");
		options.addOption("r", "detection-rate", true, 
				"sets the rate at which shots are detected in milliseconds. " +
                "this should be set to about the length of time your laser trainer " +
                "stays on for each shot, typically about 100 ms");
		options.addOption("i", "laser-intensity", true,
				"sets the intensity threshold for detecting the laser [1,255]. " +
	            "this should be as high as you can set it while still detecting " +
	            "shots");
		options.addOption("m", "marker-radius", true, 
				"sets the radius of shot markers in pixels [1,20]");
		options.addOption("c", "ignore-laser-color", true, 
				"sets the color of laser that should be ignored by ShootOFF (green " +
                "or red). No color is ignored by default");
		options.addOption("u", "use-virtual-magazine", true, 
				"turns on the virtual magazine and sets the number rounds it holds [1,45]");
		options.addOption("f", "use-malfunctions", true, 
				"turns on malfunctions and sets the probability of them happening");
		
		try {
			CommandLineParser parser = new BasicParser();
			CommandLine cmd = parser.parse(options, args);
			
			if (cmd.hasOption("d")) debugMode = true;
			
			if (cmd.hasOption("r"))
				detectionRate = Integer.parseInt(cmd.getOptionValue("r"));
			
			if (cmd.hasOption("i")) 
				laserIntensity = Integer.parseInt(cmd.getOptionValue("i"));
			
			if (cmd.hasOption("m"))
				markerRadius = Integer.parseInt(cmd.getOptionValue("m"));
			
			if (cmd.hasOption("c")) {
				ignoreLaserColor = true;
				ignoreLaserColorName = cmd.getOptionValue("c");
				
			}
			
			if (cmd.hasOption("u")) {
				useVirtualMagazine = true;
				virtualMagazineCapacity = Integer.parseInt(cmd.getOptionValue("u"));
			}
			
			if (cmd.hasOption("f")) {
				useMalfunctions = true;
				malfunctionsProbability = Float.parseFloat(cmd.getOptionValue("f"));
			}
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("com.shootoff.Main", options);
		}
		
		validateConfiguration();
	}
	
	protected void validateConfiguration() throws ConfigurationException {
		if (detectionRate < 1) {
			throw new ConfigurationException(
					String.format(DETECTION_RATE_MESSAGE, detectionRate));
		}
		
		if (laserIntensity < 1 || laserIntensity > 255) {
			throw new ConfigurationException(
					String.format(LASER_INTENSITY_MESSAGE, laserIntensity));
		}
		
		if (markerRadius < 1 || markerRadius > 20) {
			throw new ConfigurationException(
					String.format(MARKER_RADIUS_MESSAGE, laserIntensity));
		}
		
		if (ignoreLaserColor && !ignoreLaserColorName.equals("red") && 
				!ignoreLaserColorName.equals("green")) {
			throw new ConfigurationException(
					String.format(LASER_COLOR_MESSAGE, ignoreLaserColorName));
		}
		
		if (virtualMagazineCapacity < 1 || virtualMagazineCapacity > 45) {
			throw new ConfigurationException(
					String.format(VIRTUAL_MAGAZINE_MESSAGE, virtualMagazineCapacity));
		}
		
		if (malfunctionsProbability < (float)0.1 || 
				malfunctionsProbability > (float)99.9) {
			throw new ConfigurationException(
					String.format(INJECT_MALFUNCTIONS_MESSAGE, malfunctionsProbability));
		}
	}

	public void setDetectionRate(int detectionRate) {
		this.detectionRate = detectionRate;
	}
	
	public void setLaserIntensity(int laserIntensity) {
		this.laserIntensity = laserIntensity;
	}

	public void setMarkerRadius(int markRadius) {
		this.markerRadius = markRadius;
	}

	public void setIgnoreLaserColor(boolean ignoreLaserColor) {
		this.ignoreLaserColor = ignoreLaserColor;
	}

	public void setIgnoreLaserColorName(String ignoreLaserColorName) {
		this.ignoreLaserColorName = ignoreLaserColorName;
	}

	public void setUseVirtualMagazine(boolean useVirtualMagazine) {
		this.useVirtualMagazine = useVirtualMagazine;
	}

	public void setVirtualMagazineCapacity(int virtualMagazineCapacity) {
		this.virtualMagazineCapacity = virtualMagazineCapacity;
	}

	public void setMalfunctions(boolean injectMalfunctions) {
		this.useMalfunctions = injectMalfunctions;
	}

	public void setMalfunctionsProbability(float injectMalfunctionsProbability) {
		this.malfunctionsProbability = injectMalfunctionsProbability;
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}

	public int getDetectionRate() {
		return detectionRate;
	}
	
	public int getLaserIntensity() {
		return laserIntensity;
	}

	public int getMarkerRadius() {
		return markerRadius;
	}

	public boolean ignoreLaserColor() {
		return ignoreLaserColor;
	}

	public String getIgnoreLaserColorName() {
		return ignoreLaserColorName;
	}

	public boolean useVirtualMagazine() {
		return useVirtualMagazine;
	}

	public int getVirtualMagazineCapacity() {
		return virtualMagazineCapacity;
	}

	public boolean useMalfunctions() {
		return useMalfunctions;
	}

	public float getMalfunctionsProbability() {
		return malfunctionsProbability;
	}

	public boolean inDebugMode() {
		return debugMode;
	}
}