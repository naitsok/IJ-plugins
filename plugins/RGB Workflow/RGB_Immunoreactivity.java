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
import java.awt.event.*;
import java.applet.*;
import java.io.*;
import java.util.*;
import ij.plugin.frame.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.text.*;
import ij.plugin.*;
import ij.measure.*;
import ij.util.Tools;


public class RGB_Immunoreactivity implements PlugIn, ActionListener {

	// Dialog to select experiment type
	private GenericDialog gdChooseAnalysis;
	private Button btnOne3Channel;
	private Button btnDir3Channels;

	// Dialog for the selected experiment
	private GenericDialog gdAnalysis;

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

	// Image to analyze
	private ImagePlus img = null;


	public void run(String arg) {

		// Create and show dialog to select experiment type.
		gdChooseAnalysis = new GenericDialog("RGB Immunoreactivity");
		gdChooseAnalysis.setLayout(new GridLayout(3, 1, 5, 5));
		btnOne3Channel = new Button("Analyze one 3-channel image");
        btnOne3Channel.addActionListener(this);
        gdChooseAnalysis.add(btnOne3Channel);
		btnDir3Channels = new Button("Analyze directory with 3-channel images");
        btnDir3Channels.addActionListener(this);
        gdChooseAnalysis.add(btnDir3Channels);
		gdChooseAnalysis.showDialog();
		if (gdChooseAnalysis.wasCanceled())
			return;
		
	}

	public void analyze() {
		// Peforms immunoreactivity analysis on a single 3-channel image

		// Get ROI if there is one
		Roi roi = img.getRoi();

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
				// Analyze particles, first set ROI if it is in the original 3-channel image
				if (roi != null) {
					img1Ch.setRoi(roi);
				}
				IJ.run(img1Ch, "Analyze Particles...", "size=" + allParticleSizes[channel] + "-Infinity pixel show=Nothing include summarize");
			}
		}
	}

	public void showResultsInRow() {
		// Displays results in new window
		// and keeps results for all 3 channels in one row

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

	public void actionPerformed(ActionEvent e) {

		if (e.getSource() == btnOne3Channel) {
			analyzeOne3ChannelImage();
		}

		if (e.getSource() == btnDir3Channels) {
			analyzeDir3ChannelImage();
		}
		return;
	}

	private void analyzeOne3ChannelImage() {
		if (showDialogOne3ChannelImage()) {
			gdChooseAnalysis.dispose();
			analyze();
			showResultsInRow();
		}
	}

	private boolean showDialogOne3ChannelImage() {
		// Check if there are any images open
		int[] wList = WindowManager.getIDList();
       	if (wList == null) {
			// No images, do nothing
            IJ.noImage();
            return false;
		}

        	// Getting titles of images
		String[] titles = new String[wList.length];
       	for (int i = 0; i < wList.length; i++) {
            ImagePlus imp = WindowManager.getImage(wList[i]);
            if (imp != null)
             	titles[i] = imp.getTitle();
            else
             	titles[i] = "Image has no title, its index is " + Integer.toString(i + 1);
        }

		// Creating dialog to select images
		gdAnalysis = new GenericDialog("RGB Immunoreactivity");
		gdAnalysis.setLayout(new GridLayout(8, 2, 5, 5));
		gdAnalysis.addChoice("3 channel image: ", titles, titles[0]);

		tfRedThres = new TextField("" + redThres, 4);
        tfGreenThres = new TextField("" + greenThres, 4);
        tfBlueThres = new TextField("" + blueThres, 4);
		tfParticleSizeRed = new TextField(particleSizeRed, 4);
		tfParticleSizeGreen = new TextField(particleSizeGreen, 4);
		tfParticleSizeBlue = new TextField(particleSizeBlue, 4);
		
		gdAnalysis.add(new Label("Red fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfRedThres);
		gdAnalysis.add(new Label("Green fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfGreenThres);
		gdAnalysis.add(new Label("Blue fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfBlueThres);
		gdAnalysis.add(new Label("Minimum particle size for Red channel (min-Infinity):", Label.LEFT));
		gdAnalysis.add(tfParticleSizeRed);
		gdAnalysis.add(new Label("Minimum particle size for Green channel (min-Infinity):", Label.LEFT));
		gdAnalysis.add(tfParticleSizeGreen);	
		gdAnalysis.add(new Label("Minimum particle size for Blue channel (min-Infinity):", Label.LEFT));
		gdAnalysis.add(tfParticleSizeBlue);	

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
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

		// Get Image
		img = WindowManager.getImage(wList[gdAnalysis.getNextChoiceIndex()]);

		return true;
	}

	private void analyzeDir3ChannelImage() {
		if (showDialogDir3ChannelImage()) {
			gdChooseAnalysis.dispose();

			String dir = IJ.getDirectory("Choose a Directory with 3 Channel Images...");
			String[] files = new File(dir).list();

			for (int i = 0; i < files.length; i++) {
				img = IJ.openImage(new File(dir, files[i]).getPath());
				IJ.showStatus("Calculating for " + img.getTitle() +" out of total " + files.length + " images");
				analyze();
				IJ.showProgress((double)i / files.length);
			}
			showResultsInRow();
		}
	}

	private boolean showDialogDir3ChannelImage() {

		// Creating dialog to select images
		gdAnalysis = new GenericDialog("Immunoreactivity RGB");
		gdAnalysis.setLayout(new GridLayout(7, 2, 5, 5));

		tfRedThres = new TextField("" + redThres, 4);
        tfGreenThres = new TextField("" + greenThres, 4);
        tfBlueThres = new TextField("" + blueThres, 4);
		tfParticleSizeRed = new TextField(particleSizeRed, 4);
		tfParticleSizeGreen = new TextField(particleSizeGreen, 4);
		tfParticleSizeBlue = new TextField(particleSizeBlue, 4);
		
		gdAnalysis.add(new Label("Red fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfRedThres);
		gdAnalysis.add(new Label("Green fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfGreenThres);
		gdAnalysis.add(new Label("Blue fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfBlueThres);
		gdAnalysis.add(new Label("Minimum particle size for Red channel (min-Infinity):", Label.LEFT));
		gdAnalysis.add(tfParticleSizeRed);
		gdAnalysis.add(new Label("Minimum particle size for Green channel (min-Infinity):", Label.LEFT));
		gdAnalysis.add(tfParticleSizeGreen);	
		gdAnalysis.add(new Label("Minimum particle size for Blue channel (min-Infinity):", Label.LEFT));
		gdAnalysis.add(tfParticleSizeBlue);	

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
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
