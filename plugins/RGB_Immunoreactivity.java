// RGB_Immunoreactivity plugin for ImageJ
// Counts the number of particles (cells) in each channel of 3-channel images.
// Performs the analysis on all the images in the specified directory.
//
// Tested on ImageJ 1.50i and higher (Java 1.8 or higher is required)
// To use the RGB_Immunoreactivity.java, it first must be compiled:
// 1) Copy into ImajeJ plugin directory
// 2) From ImageJ menu, Plugins -> Compile and Run -> select the RGB_Immunoreactivity.java
// 3) Restart ImageJ to get the plugin under the Plugins menu
// NOTE: FIJI does not support compiling Java files. 
// For use in FIJI, compiled RGB_Immunoreactivity.class
// files must be copied to FIJI's plugins directory.
//
// Developed by Konstantin Tamarov and Mariia Ivanova
// Copyright 2021
// The plugin can be freely downloaded, modified and redistributed with the appropriate credits given to the authors
// License: MIT

import java.awt.*;
import java.io.*;
import ij.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.process.*;
import ij.text.*;


public class RGB_Immunoreactivity implements PlugIn {

	// Dialog to set the thresholds for each channel
	private GenericDialog gd;

	// Thresholds
	private int redThres = 75, greenThres = 75, blueThres = 75;
	private int[] allThresholds = new int[3];
	private TextField tfRedThres, tfGreenThres, tfBlueThres;

	// Particle size
	private String particleSizeRed = "100";
	private TextField tfParticleSizeRed;
	private String particleSizeGreen = "100";
	private TextField tfParticleSizeGreen;
	private String particleSizeBlue = "100";
	private TextField tfParticleSizeBlue;
	private String[] allParticleSizes = new String[3];

	public void run(String arg) {
		if (!showDialog())
			return;

		String dir = IJ.getDirectory("Choose a Directory...");
		String[] files = new File(dir).list();
		// IJ.showMessage(String.join(" ", files));
		ImagePlus img = null;
		for (int i = 0; i < files.length; i++) {
			img = IJ.openImage(new File(dir, files[i]).getPath());
			IJ.showStatus("Calculating for " + img.getTitle() +" out of total " + files.length + " images");
			// img.show();
			// process image only if it is 3-channel RGB
			if (img.getType() == ImagePlus.COLOR_RGB) {
				// process image per channel
				ImagePlus img1Ch = null;
				for (int channel = 0; channel < 3; channel++) {

					// select image and get the channel from it
					img1Ch = ChannelSplitter.split(img)[channel];
					img1Ch.setTitle(img.getTitle() + channelIntToStr(channel));

					// Convert image to mask
					IJ.setThreshold(img1Ch, allThresholds[channel], 255);
					IJ.run(img1Ch, "Convert to Mask", "");
					// img1Ch.show();

					IJ.run(img1Ch, "Analyze Particles...", "size=" + allParticleSizes[channel] + "-Infinity pixel show=Nothing include summarize");
				}
			}
			IJ.showProgress((double)i / files.length);
		}
		// Get data from Summary window and arrange it so that all three channels in one image is in one row
		ResultsTable resTable = ResultsTable.getResultsTable("Summary");
		
		// loop through results and join into rows each there channels
		String[] resultsInRow = new String[resTable.size() / 3];
		for (int i = 0; i < resTable.size() / 3; i++) {
			resultsInRow[i] = resTable.getRowAsString(3 * i) + "\t" + resTable.getRowAsString(3 * i + 1) + "\t" + resTable.getRowAsString(3 * i + 2);
		}

		new TextWindow("Immunoreactivity Results", 
			resTable.getColumnHeadings() + "\t" + resTable.getColumnHeadings() + "\t" + resTable.getColumnHeadings(),
			String.join("\n", resultsInRow),
			1500, 900);
	}

	private boolean showDialog() {

		// Creating dialog to select images
		gd = new GenericDialog("Immunoreactivity RGB");
		gd.setLayout(new GridLayout(7, 2, 5, 5));

		tfRedThres = new TextField("" + redThres, 4);
        tfGreenThres = new TextField("" + greenThres, 4);
        tfBlueThres = new TextField("" + blueThres, 4);
		tfParticleSizeRed = new TextField(particleSizeRed, 4);
		tfParticleSizeGreen = new TextField(particleSizeGreen, 4);
		tfParticleSizeBlue = new TextField(particleSizeBlue, 4);
		
		gd.add(new Label("Red fluorescense noise threshold (0-255):", Label.LEFT));
		gd.add(tfRedThres);
		gd.add(new Label("Green fluorescense noise threshold (0-255):", Label.LEFT));
		gd.add(tfGreenThres);
		gd.add(new Label("Blue fluorescense noise threshold (0-255):", Label.LEFT));
		gd.add(tfBlueThres);
		gd.add(new Label("Minimum particle size for Red channel (min-Infinity):", Label.LEFT));
		gd.add(tfParticleSizeRed);
		gd.add(new Label("Minimum particle size for Green channel (min-Infinity):", Label.LEFT));
		gd.add(tfParticleSizeGreen);	
		gd.add(new Label("Minimum particle size for Blue channel (min-Infinity):", Label.LEFT));
		gd.add(tfParticleSizeBlue);	

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());
		allThresholds[0] = redThres; allThresholds[1] = greenThres; allThresholds[2] = blueThres;

		// Get particle size
		particleSizeRed = tfParticleSizeRed.getText();
		particleSizeGreen = tfParticleSizeGreen.getText();
		particleSizeBlue = tfParticleSizeBlue.getText();
		allParticleSizes[0] = particleSizeRed; allParticleSizes[1] = particleSizeGreen; allParticleSizes[2] = particleSizeBlue;

		return true;
	}

	private String channelIntToStr(int channel) {
		if (channel == 0)
			return " (red)";
		if (channel == 1)
			return " (green)";
		if (channel == 2)
			return " (blue)";
		return "";
	}

	private ImagePlus to8bitChannel(ImagePlus image, int channel) {
		// Gets specified channel from image
		String tempTitle = image.getTitle();
		if (image.getType() == ImagePlus.COLOR_RGB) {
			image = ChannelSplitter.split(image)[channel];
			image.setTitle(tempTitle);
			return image;
		}
		
		if (image.getType() != ImagePlus.GRAY8) {
			ImageConverter ic = new ImageConverter(image);
			ic.convertToGray8();
			return image;
		}

		return image;
	}
}
