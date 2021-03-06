// RGB_Colocalizer plugin for ImageJ
// Performs colocalization analysis for 3 types of data available:
// 1) For separate Red, Green and Blue channels
// 2) For one 3-channel image
// 3) For directory containing 3-channel images
//
// Tested on ImageJ 1.50i and higher (Java 1.8 or higher is required)
// To use the RGB_Colocalizer.java, it first must be compiled:
// 1) Copy into ImajeJ plugin directory
// 2) From ImageJ menu, Plugins -> Compile and Run -> select the RGB_colocalizer.java
// 3) Restart ImageJ to get the plugin under the Plugins menu
// NOTE: FIJI does not support compiling Java files. 
// For use in FIJI, compiled RGB_Colocalizer.class and RGB_Colocalizer$ColocResult.class 
// files must be copied to FIJI's plugins directory.
// 
// Improved from Apoptosis Correlator (http://sibarov.da.ru)
// Additional inspiration from JACoP (https://imagej.nih.gov/ij/plugins/track/jacop.html)
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


public class RGB_Colocalizer implements PlugIn, ActionListener, Measurements {
	// Colocalizes pair of channels, plots and shows colocalization images if necessary
	// If all the three channels are available, performs three channel colocalization
	// Three channle colocalization is the colocalization of Blue channel (typically DAPI)
	// and the result of colocalization of Red and Green channels

	// Image stacks and images slices
	private ImageStack iStackR, iStackG, iStackB;
	private ImagePlus iR, iG, iB;
	private ImageStatistics iStats;

	// Colocolization images for each pair of channels
	private ImagePlus iRvsG, iRvsB, iGvsB;
	
	// Titles if images
	private String[] titles;

	// If true displays color coded colocolization plot
	private boolean showColorColoc = false;

	// If true displays intensity coded colocolization plot in logarithmic scale
	private boolean showIntensityColoc = false;

	// If true displays all the channels in one row
	// If false, channels go in rows one after another
	private boolean showChannelsInOneRow = true;

	// If true displays colocalized image
	private boolean showColocImage = false;

	// Dialog to select experiment type: analyze channels separately, analyze one 3 channel image, or analyze directory with 3 channel images
	private GenericDialog gdChooseAnalysis;
	private Button btnSeparateChannels;
	private Button btnOne3Channel;
	private Button btnDir3Channels;

	// Dialog for the selected experiment
	private GenericDialog gdAnalysis;

	// Thresholds
	private int redThres = 75, greenThres = 75, blueThres = 75;
	private Button btnAutoThresSeparateChannels;
	private Button btnAutoThresOne3Channel;
	private TextField tfRedThres, tfGreenThres, tfBlueThres;

	// Colors for plotting
	private static int WHITE = 0xFFFFFF;
	private static int RED = 0xFF0000;
	private static int GREEN = 0x00FF00;
	private static int BLUE = 0x0000FF;
	private static int BLACK = 0x000000;
	private static int GRAY = 0x808080;
	private static int ORANGE = 0xFF8000;

	// Results
	private String resultsTitle = "Image titles for colocalization\tSlice #\tCh1 vs Ch2\tCh1 pixels\tCh2 pixels\tColoc pixels\tPercent Ch1\tPercent Ch2\tPercent Coloc\tCh1 Overlap Ch2\tCh2 Overlap Ch1\t";
	private String rgTitle = "%s vs %s\tRed pixels\tGreen pixels\tColoc pixels\tPerc Red\tPerc Green\tPerc Coloc\tRed Overlap Green\tGreen Overlap Red\t";
	private String rbTitle = "%s vs %s\tRed pixels\tBlue pixels\tColoc pixels\tPerc Red\tPerc Blue\tPerc Coloc\tRed Overlap Blue\tBlue Overlap Red\t";
	private String gbTitle = "%s vs %s\tGreen pixels\tBlue pixels\tColoc pixels\tPerc Green\tPerc Blue\tPerc Coloc\tGreen Overlap Blue\tBlue Overlap Green\t";
	private String allChTitle = "%s vs %s\tBlue pixels\tGreen/Red pixels\tColoc pixels\tPerc Blue\tPerc Green/Red\tPerc Coloc\tBlue Overlap Green/Red\tGreen/Red Overlap Blue\t";
	private String dataFormat = "%s\t%d\t%s\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.5f\t%.5f\t";
	private String final2ChResutsTitle = "";
	private ArrayList<String> allLst2ChResults = new ArrayList<String>();
	private ArrayList<String> allLst3ChResults = new ArrayList<String>();

	public void run(String arg) {

		// Create and show dialog to select experiment type.
		gdChooseAnalysis = new GenericDialog("RGB Image Correlator");
		gdChooseAnalysis.setLayout(new GridLayout(4, 1, 5, 5));
		btnSeparateChannels = new Button("Analyze channels separately");
        btnSeparateChannels.addActionListener(this);
        gdChooseAnalysis.add(btnSeparateChannels);
		btnOne3Channel = new Button("Analyze one 3-channel image");
        btnOne3Channel.addActionListener(this);
        gdChooseAnalysis.add(btnOne3Channel);
		btnDir3Channels = new Button("Analyze directory with 3-channel images");
        btnDir3Channels.addActionListener(this);
        gdChooseAnalysis.add(btnDir3Channels);
		gdChooseAnalysis.showDialog();
		if (gdChooseAnalysis.wasCanceled())
			return;
		// IJ.showMessage("My_Plugin","Hello world!");
	}

	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == btnSeparateChannels) {
			analyzeSeparateChannels();
		}

		if (e.getSource() == btnOne3Channel) {
			analyzeOne3ChannelImage();
		}

		if (e.getSource() == btnDir3Channels) {
			analyzeDir3ChannelImage();
		}

		if (e.getSource() == btnAutoThresSeparateChannels) {
			// Auto thresholds selected images
			Vector choices = gdAnalysis.getChoices();
			redThres = this.getAutoThres((Choice)choices.elementAt(0), 0, tfRedThres);
			greenThres = this.getAutoThres((Choice)choices.elementAt(1), 1, tfGreenThres);
			blueThres = this.getAutoThres((Choice)choices.elementAt(2), 2, tfBlueThres);
		}

		if (e.getSource() == btnAutoThresOne3Channel) {
			// Auto threshold one 3 channel image
			Choice choice = (Choice)gdAnalysis.getChoices().elementAt(0);
			redThres = this.getAutoThres(choice, 0, tfRedThres);
			greenThres = this.getAutoThres(choice, 1, tfGreenThres);
			blueThres = this.getAutoThres(choice, 2, tfBlueThres);
		}

		return;
	}

	private void analyze() {
		// First do the colocalization for each pair of channels if images are existing
		final2ChResutsTitle = resultsTitle;
		ArrayList<String> lst2ChResults = new ArrayList<String>();
		ColocResult result = null;	
		
		if (iR != null && iB != null) {
			result = colocalize(iR, iB, redThres, blueThres, RED, BLUE, "Red", "Blue");
			lst2ChResults = this.appendResults(lst2ChResults, result.toTextRows(dataFormat), showChannelsInOneRow);
			if (showChannelsInOneRow) final2ChResutsTitle = final2ChResutsTitle + resultsTitle;
		}
		if (iG != null && iB != null) {
			result = colocalize(iG, iB, greenThres, blueThres, GREEN, BLUE, "Green", "Blue");
			lst2ChResults = this.appendResults(lst2ChResults, result.toTextRows(dataFormat), showChannelsInOneRow);
			if (showChannelsInOneRow) final2ChResutsTitle = final2ChResutsTitle + resultsTitle;
		}
		if (iR != null && iG != null) {
			result = colocalize(iR, iG, redThres, greenThres, RED, GREEN, "Red", "Green");
			lst2ChResults = this.appendResults(lst2ChResults, result.toTextRows(dataFormat), showChannelsInOneRow);
		}
		allLst2ChResults.addAll(lst2ChResults);
		
		// now do the blue (DAPI) colocalization with the result of colocalization of red and green channel
		// result variable now contains colocalization of Red and Green channels
		if (iR != null && iG != null && iB != null) {
			showColorColoc = false; showIntensityColoc = false; // does not make sense because Greed/Red coloc image is mask
			result = colocalize(iB, new ImagePlus("Colocalized Red and Green channels", result.iCh1vsCh2Stack), blueThres, 100, BLUE, ORANGE, "Blue", "Red/Green colocalized");
		}
		Collections.addAll(allLst3ChResults, result.toTextRows(dataFormat));

	}

	private void showResults() {
		// Colocalization for each pair of channels
		new TextWindow("Colocalization statisitics for pair of channels",
		final2ChResutsTitle, 
		String.join("\n", allLst2ChResults), 
		1500, 900);

		// Colocalization for Blue (DAPI) vs colocalized Red and Green
		new TextWindow("Colocalization statistics for three channels",
			resultsTitle,
			String.join("\n", allLst3ChResults),
			1500, 900);
		return;
	}

	private void analyzeSeparateChannels() {
		if(showDialogSeparateChannels()) {
			gdChooseAnalysis.dispose();
			analyze();
			showResults();
		}
	}

	private boolean showDialogSeparateChannels() {

		// Check if there are any images open
		int[] wList = WindowManager.getIDList();
       	if (wList == null) {
			// No images, do nothing
            IJ.noImage();
            return false;
		}

        	// Getting titles of images
		titles = new String[wList.length + 1];
		titles[0] = "No Image";
       		for (int i = 0; i < wList.length; i++) {
            		ImagePlus imp = WindowManager.getImage(wList[i]);
            		if (imp != null)
                		titles[i + 1] = imp.getTitle();
            		else
               	 	titles[i + 1] = "Image has no title, its index is " + Integer.toString(i + 1);
        	}

		// Creating dialog to select images
		gdAnalysis = new GenericDialog("RGB Image Correlator");
		gdAnalysis.setLayout(new GridLayout(12, 2, 5, 5));
		gdAnalysis.addChoice("Red Channel: ", titles, titles[1]);
		gdAnalysis.addChoice("Green Channel: ", titles, titles.length > 2 ? titles[2] : titles[0]);
		gdAnalysis.addChoice("Blue Channel: ", titles, titles.length > 3 ? titles[3] : titles[0]);

		tfRedThres = new TextField("" + redThres, 4);
        tfGreenThres = new TextField("" + greenThres, 4);
        tfBlueThres = new TextField("" + blueThres, 4);
		
		gdAnalysis.add(new Label("Red fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfRedThres);
		gdAnalysis.add(new Label("Green fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfGreenThres);
		gdAnalysis.add(new Label("Blue fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfBlueThres);		

		gdAnalysis.add(new Label("")); // placeholder label
		btnAutoThresSeparateChannels = new Button("Auto threshold images");
        btnAutoThresSeparateChannels.addActionListener(this);
        gdAnalysis.add(btnAutoThresSeparateChannels);
		
		// Settings
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show color coded colocalization plot", showColorColoc); // NOTE: this plot is not reflecting true colocolization percentages for now
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show logarithmic intensity colocalization plot", showIntensityColoc);
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show colocalized image", showColocImage);
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show data for all channels in one row", showChannelsInOneRow);
		

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
			return false;

		// Get images and their titles
		String tempTitle = "";
		int idxR = gdAnalysis.getNextChoiceIndex();
		if (idxR > 0) { iR = to8bitChannel(WindowManager.getImage(wList[idxR - 1]), 0); } else { iR = null; }
		int idxG = gdAnalysis.getNextChoiceIndex();
		if (idxG > 0) { iG = to8bitChannel(WindowManager.getImage(wList[idxG - 1]), 1); } else { iG = null; }
		int idxB = gdAnalysis.getNextChoiceIndex();
		if (idxB > 0) { iB = to8bitChannel(WindowManager.getImage(wList[idxB - 1]), 2); } else { iB = null; }

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());

		// Get boolean values
		showColorColoc = gdAnalysis.getNextBoolean();
		showIntensityColoc = gdAnalysis.getNextBoolean();
		showColocImage = gdAnalysis.getNextBoolean();
		showChannelsInOneRow = gdAnalysis.getNextBoolean();

		return true;
	}

	private void analyzeOne3ChannelImage() {
		if (showDialogOne3ChannelImage()) {
			gdChooseAnalysis.dispose();
			analyze();
			showResults();
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
		titles = new String[wList.length];
       	for (int i = 0; i < wList.length; i++) {
            ImagePlus imp = WindowManager.getImage(wList[i]);
            if (imp != null)
             	titles[i] = imp.getTitle();
            else
             	titles[i] = "Image has no title, its index is " + Integer.toString(i + 1);
        }

		// Creating dialog to select images
		gdAnalysis = new GenericDialog("RGB Image Correlator");
		gdAnalysis.setLayout(new GridLayout(10, 2, 5, 5));
		gdAnalysis.addChoice("3 channel image: ", titles, titles[0]);

		tfRedThres = new TextField("" + redThres, 4);
        tfGreenThres = new TextField("" + greenThres, 4);
        tfBlueThres = new TextField("" + blueThres, 4);
		
		gdAnalysis.add(new Label("Red fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfRedThres);
		gdAnalysis.add(new Label("Green fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfGreenThres);
		gdAnalysis.add(new Label("Blue fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfBlueThres);		

		gdAnalysis.add(new Label("")); // placeholder label
		btnAutoThresOne3Channel = new Button("Auto threshold image");
        btnAutoThresOne3Channel.addActionListener(this);
        gdAnalysis.add(btnAutoThresOne3Channel);
		
		// Settings
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show color coded colocalization plot", showColorColoc); // NOTE: this plot is not reflecting true colocolization percentages for now
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show logarithmic intensity colocalization plot", showIntensityColoc);
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show colocalized image", showColocImage);
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show data for all channels in one row", showChannelsInOneRow);
		

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
			return false;

		// Get images and their titles
		String tempTitle = "";
		int idxImage = gdAnalysis.getNextChoiceIndex();
		iR = to8bitChannel(WindowManager.getImage(wList[idxImage]), 0);
		iG = to8bitChannel(WindowManager.getImage(wList[idxImage]), 1);
		iB = to8bitChannel(WindowManager.getImage(wList[idxImage]), 2);

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());

		// Get boolean values
		showColorColoc = gdAnalysis.getNextBoolean();
		showIntensityColoc = gdAnalysis.getNextBoolean();
		showColocImage = gdAnalysis.getNextBoolean();
		showChannelsInOneRow = gdAnalysis.getNextBoolean();

		return true;
	}

	private void analyzeDir3ChannelImage() {
		if (showDialogDir3ChannelImage()) {
			gdChooseAnalysis.dispose();

			String dir = IJ.getDirectory("Choose a Directory with 3 Channel Images...");
			String[] files = new File(dir).list();

			ImagePlus img = null;
			for (int i = 0; i < files.length; i++) {
				img = IJ.openImage(new File(dir, files[i]).getPath());
				IJ.showStatus("Calculating for " + img.getTitle() +" out of total " + files.length + " images");

				// process image only if it is 3-channel RGB
				if (img.getType() == ImagePlus.COLOR_RGB) {
					// process image per channel
					iR = to8bitChannel(img, 0);
					iG = to8bitChannel(img, 1);
					iB = to8bitChannel(img, 2);
					analyze();
				}
				IJ.showProgress((double)i / files.length);
			}
			showResults();
		}
	}

	private boolean showDialogDir3ChannelImage() {

		// Creating dialog to select thresholds and settings
		gdAnalysis = new GenericDialog("RGB Image Correlator");
		gdAnalysis.setLayout(new GridLayout(8, 2, 5, 5));

		tfRedThres = new TextField("" + redThres, 4);
        tfGreenThres = new TextField("" + greenThres, 4);
        tfBlueThres = new TextField("" + blueThres, 4);
		
		gdAnalysis.add(new Label("Red fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfRedThres);
		gdAnalysis.add(new Label("Green fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfGreenThres);
		gdAnalysis.add(new Label("Blue fluorescense noise threshold (0-255):", Label.LEFT));
		gdAnalysis.add(tfBlueThres);
		
		// Settings
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show color coded colocalization plot", showColorColoc); // NOTE: this plot is not reflecting true colocolization percentages for now
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show logarithmic intensity colocalization plot", showIntensityColoc);
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show colocalized image", showColocImage);
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Show data for all channels in one row", showChannelsInOneRow);

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
			return false;

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());

		// Get boolean values
		showColorColoc = gdAnalysis.getNextBoolean();
		showIntensityColoc = gdAnalysis.getNextBoolean();
		showColocImage = gdAnalysis.getNextBoolean();
		showChannelsInOneRow = gdAnalysis.getNextBoolean();

		return true;
	}

	private ArrayList<String> appendResults(ArrayList<String> existingResults, String[] newResults, boolean addInRow) {
		ArrayList<String> combinedResults = new ArrayList<String>();
		if (addInRow) {
			if (existingResults.size() == 0) {
				combinedResults = existingResults;
				Collections.addAll(combinedResults, newResults);
			}
			else {
				// Select the shortest array and add results on the left side of each existingResults row
				int resSize = existingResults.size();
				int newSize = newResults.length;
				if (newSize < resSize) resSize = newSize;
				for (int i = 0; i < resSize; i++) {
					combinedResults.add(existingResults.get(i) + newResults[i]);
				}
			}			
		}
		else {
			combinedResults = existingResults;
			Collections.addAll(combinedResults, newResults);
		}
		return combinedResults;
	}

	public ColocResult colocalize(ImagePlus img1, ImagePlus img2, 
		int thres1, int thres2, 
		int color1, int color2,
		String ch1Title, String ch2Title) {

		// Colocalize two image stacks
		// Returns array of strings with results to be dispalyed
		// Array contains title string (with channel and image titles)
		// and string with data for each slice in the stack.
		
		// Get stacks
		ImageStack img1Stack = img1.getStack();
		ImageStack img2Stack = img2.getStack();
		
		// Start with getting number of slices in each stack and selecting the smaller one
		int stackSize = img1Stack.getSize();
		int size2 = img2Stack.getSize();
		if (size2 < stackSize) stackSize = size2;

		// height and width if images in stack in pixels
		int width = img1Stack.getWidth();
		int height = img1Stack.getHeight();
		if ((width != img2Stack.getWidth()) || (height != img2Stack.getHeight())) {
			IJ.showMessage("RGB Correlator", img1.getTitle() + " and " + img2.getTitle() + 	
				" are not of the same pixel size!");
			return null;
		}

		ColocResult result = new ColocResult(img1, img2, ch1Title, ch2Title, stackSize);

		// Image processors for images and correlation graphs for each image in the stack
		ImageProcessor img1Proc = null, img2Proc = null;
		ImageProcessor colocProc = null;
		ImageProcessor colocCounts = null;
		ImageProcessor colocColorCode = null;
		ImageProcessor colocIntensity = null;

		// count keeps number of pixes with the same intensity for colocCounts
		int count = 0;
		// pixel intensity values for slices in image1 and image2
		int z1 = 0, z2 = 0;
		
		for (int i = 1; i <= stackSize; i++) {
			// Loop through img1 and img2 stack to find correlation
			img1Proc = img1Stack.getProcessor(i);
			img2Proc = img2Stack.getProcessor(i);
			colocProc = new ColorProcessor(width, height);
			colocCounts = new FloatProcessor(256, 256); // this will keep counts for each pixel intensity in each image in stack
			colocColorCode = new ColorProcessor(256, 256); // this will color codes for each pixel intensity in each image in stack
			colocIntensity = new ColorProcessor(256, 256); // to plot instensity map based on number of counts in colocCounts
			// colocColorCode.setColor(WHITE); // not working
			// init maxCount for slice i to 0
			int tempCount = 0;
			result.maxCounts[i - 1] = 0;
			
			
			for (int y = 0; y < height; y++) { // Loop through Y coordinate
                		for (int x = 0; x < width; x++) { // Loop through X coordinate

					// z-value of pixel (x, y) in img1Stack on slice i
					z1 = (int)img1Proc.getPixelValue(x, y); 
					// z-value of pixel (x, y) in img2Stack on slice i
					z2 = (int)img2Proc.getPixelValue(x, y); // z-value of pixel (x,y) in img2Stack on slice i

					// incement count for pixel (z1, z2) in corrPlot
					// 255 - z2 is needed because coordinates of pixels in image start from top left corner
					tempCount = (int)colocCounts.getPixelValue(z1, 255 - z2) + 1;
					colocCounts.putPixelValue(z1, 255 - z2, tempCount);
					// set maxCount for slice i if necessary
					if (tempCount > result.maxCounts[i - 1]) result.maxCounts[i - 1] = tempCount;

					if (z1 < thres1 && z2 < thres2) {
						// Pixel values z1 and z2 are both below set thresholds, color is black
						colocColorCode.putPixel(z1, 255 - z2, GRAY);
						result.countsBelowThres[i - 1] = result.countsBelowThres[i - 1] + 1;
					}
					if (z1 >= thres1 && z2 < thres2) {
						// Pixel values z1 are above threshold, color is for z1
						colocColorCode.putPixel(z1, 255 - z2, color1);
						result.ch1Counts[i - 1] = result.ch1Counts[i - 1] + 1;
					}
					if (z1 < thres1 && z2 >= thres2) {
						// Pixel values z2 are above threshold, color is for z2
						colocColorCode.putPixel(z1, 255 - z2, color2);
						result.ch2Counts[i - 1] = result.ch2Counts[i - 1] + 1;
					}
					if (z1 >= thres1 && z2 >= thres2) {
						// Pixel values z1 and z2 are both above threshold and colocolized, color is combination for z1 and z2
						colocColorCode.putPixel(z1, 255 - z2, (int)((color1 + color2) * 0.6));
						result.colocCounts[i - 1] = result.colocCounts[i - 1] + 1;
						// also add white pixel for the colocalized image
						colocProc.putPixel(x, y, WHITE);
					}
				}
			}

			// Create intensity plot from colocCounts and using maxCount
			for (int x = 0; x < 256; x++) { // Loop through Y coordinate
                		for (int y = 0; y < 256; y++) {
					// Make intensity plot in lof scale for better visualization
					colocIntensity.putPixel(x, y, getIntensityColor(Math.log((double)colocCounts.getPixelValue(x, y) + 1) / Math.log((double)result.maxCounts[i - 1])));
				}
			}
			
			// Draw thresholds on the correlation color plot and intensity plot
			for(int j = 0; j < 256; j++) { 
				colocColorCode.putPixel(j, 255 - thres2, BLACK);
				colocIntensity.putPixel(j, 255 - thres2, BLACK);
			}
            for(int j = 0; j < 256; j++) { 
				colocColorCode.putPixel(thres1, j, BLACK);
				colocIntensity.putPixel(thres1, j, BLACK);
			}

			// Some transformations
			colocColorCode.flipVertical();
			colocColorCode.rotate(-90);

			colocIntensity.flipVertical();
			colocIntensity.rotate(-90);
			
			// Add colocCounts, colocColorCode and colocIntensity to corresponding stacks
			result.corrStack.addSlice("Correlation plot for slice " + i, colocCounts);
			result.corrColorStack.addSlice("Correlation color plot for slice " + i, colocColorCode);
			result.intensityStack.addSlice("Correlation intensty plot for slice " + i, colocIntensity);
			result.iCh1vsCh2Stack.addSlice("Colocalization for slice " + i, colocProc);
			
			// Calculate correlation percentages and overlap coefficients
			result.countsAboveThres[i - 1] = result.ch1Counts[i - 1] + result.ch2Counts[i - 1] + result.colocCounts[i - 1];
			result.percCh1NoCh2[i - 1] = (double)result.ch1Counts[i - 1] / (double)result.countsAboveThres[i - 1] * 100;
			result.percCh2NoCh1[i - 1] = (double)result.ch2Counts[i - 1] / (double)result.countsAboveThres[i - 1] * 100;
			result.percColoc[i - 1] = (double)result.colocCounts[i - 1] / (double)result.countsAboveThres[i - 1] * 100;
			result.ch1OverlapCh2[i - 1] = (double)result.colocCounts[i - 1] / (double)(result.ch1Counts[i - 1] + result.colocCounts[i - 1]);
			result.ch2OverlapCh1[i - 1] = (double)result.colocCounts[i - 1] / (double)(result.ch2Counts[i - 1] + result.colocCounts[i - 1]);

			// Show status
			IJ.showProgress((double)i / stackSize);
            IJ.showStatus("Calculating correlation for slice " + i +"/" + stackSize);
			
		}
		IJ.showProgress(1.0);

		if (showColorColoc)
			new ImagePlus("Color correlation plot for " + ch1Title + " vs " + ch2Title + " channels", result.corrColorStack).show();
		if (showIntensityColoc)
			new ImagePlus("Intesity correlation plot for " + ch1Title + " vs " + ch2Title + " channels", result.intensityStack).show();
		if (showColocImage)
			new ImagePlus("Colocalization image for " + ch1Title + " vs " + ch2Title + " channels", result.iCh1vsCh2Stack).show();

		return result;
	}

	public int getIntensityColor(double ratio) {
		// calulates color for pixel in the intensity plot
		int red = 0;
		int green = 0;
		int blue = 0;
	
		if (ratio <= 0.1) {
			ratio = ratio / 0.1;
			red = (int)(188 * ratio);
			green = (int)(110 * ratio);
			blue = (int)(209 * ratio);
		}
		if (ratio > 0.1 && ratio <= 0.7) {
			ratio = (ratio - 0.1) / 0.6;
			red = (int)(255 * ratio + 188 * (1 - ratio));
			green = (int)(174 * ratio + 110 * (1 - ratio));
			blue = (int)(209 * (1 - ratio));
		}
		if (ratio > 0.7) {
			ratio = (ratio - 0.7) / 0.3;
			red = 255;
			green = (int)(252 * ratio + 174 * (1 - ratio));
			blue = (int)(246 * ratio);
		}


		int rgb = (red << 16 | green << 8 | blue);
		return rgb;
	}
	public int getIntensityColor2(double ratio) {
		int red = (int) (255 * ratio);
		int green = (int) (255 * ratio);
		int blue = (int) (255 * ratio);

		int rgb = (red << 16 | green << 8 | blue);
		return rgb;
	}

	public int getAutoThres(Choice choice, int channel, TextField tf) {
		int thres = 75; // default value for noise
		// Get selected image index
		int imgIdx = 0;
		imgIdx = choice.getSelectedIndex();

		if (choice.getItem(0) == "No Image")
			imgIdx = imgIdx - 1;
		
		if (imgIdx >= 0) {
			// The image was selected
			// Get images based on selection
			int[] wList = WindowManager.getIDList();
			
			ImagePlus image = WindowManager.getImage(wList[imgIdx]);
			ImagePlus image8bit = to8bitChannel(image, channel);

			ImageStack stack = image8bit.getStack();

			// Get image from the middle of stack if it is stack
			int stackSize = stack.getSize();
			int stackIdx = 0;
			if (stackSize > 1) {
				stackIdx = (int)(stackSize / 2);
			}
			// Get image processor for image in the middle of the stack
			ImageProcessor ip = stack.getProcessor(stackIdx + 1);
			ImageStatistics stats = image8bit.getStatistics();
			thres = ip.getAutoThreshold(stats.histogram);

		}

		tf.setText("" + thres);
		return thres;
	}

	public ImagePlus to8bitChannel(ImagePlus image, int channel) {
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
	
	class ColocResult {
		// Keeps all the colocalization results for pair of images
		
		// Titles of colocalized images
		public String i1Title, i2Title;
		// Titles of channels to colocalize
		public String ch1Title, ch2Title;
		// Image with colocalization
		public ImageStack iCh1vsCh2Stack;
		// Stack plots to keep correlation plots for each slice
		// The coordinates are 0-255 because of color intensity
		public ImageStack corrStack;
		// Color codes for stack plot
		public ImageStack corrColorStack;
		// Intesnity stack plot for each slice
		public ImageStack intensityStack;
		// Number of slices
		public int nSlices;
		// Formatted string with the statistics for each slice to be put into final results window with table
		// public String[] strColoc;
		// maximum count for number of pixels for certain intensity for each slice
		public int[] maxCounts;
		// All counts for colocalization in two channels above the threshold for each slice
		public int[] countsAboveThres;
		// Counts for both channel 1 and channel 2 that are below threshold for each slice
		public int[] countsBelowThres;
		// Counts for channel 1 above the threshold for each slice
		public int[] ch1Counts;
		// Counts for channel 2 above the threshold for each slice
		public int[] ch2Counts;
		// Counts for colocalized pixels above threshold for each slice
		public int[] colocCounts;
		// Percentage of counts above threshold for channel 1 without channel 2 for each slice
		public double[] percCh1NoCh2;
		// Percentage of counts above threshold for channel 2 without channel 1 for each slice
		public double[] percCh2NoCh1;
		// Percentage of colocalized counts above threshold for channel 1 and channel 2 for each slice
		public double[] percColoc;
		// Fraction of channel 1 overlapping channel 2 (M1 coefficient) for each slice
		public double[] ch1OverlapCh2;
		// Fraction of channel 2 overlapping channel 1 (M2 coefficient) for each slice
		public double[] ch2OverlapCh1;

		public ColocResult(ImagePlus img1, ImagePlus img2,
			String chan1Title, String chan2Title, int numSlices) {
			// Creates an empty ColocResults for two images specified by titles
			// numSlices is number of slices in the images
			i1Title = img1.getTitle();
			i2Title = img2.getTitle();
			ch1Title = chan1Title;
			ch2Title = chan2Title;
			
			// Create the image stack for colocolized image
			ImageStack img1Stack = img1.getStack();
			iCh1vsCh2Stack = new ImageStack(img1Stack.getWidth(), img1Stack.getHeight());

			// Stack plots to keep correlation plots for each slice
			// The coordinates are 0-255 because of color intensity
			corrStack = new ImageStack(256, 256);
			// Color codes for stack plot
			corrColorStack = new ImageStack(256, 256);
			// Intesnity stack plot for each slice
			intensityStack = new ImageStack(256, 256);

			nSlices = numSlices;
			// strColoc = new String[nSlices];
			maxCounts = new int[nSlices];
			countsAboveThres = new int[nSlices];
			countsBelowThres = new int[nSlices];
			ch1Counts = new int[nSlices];
			ch2Counts = new int[nSlices];
			colocCounts = new int[nSlices];
			percCh1NoCh2 = new double[nSlices];
			percCh2NoCh1 = new double[nSlices];
			percColoc = new double[nSlices];
			ch1OverlapCh2 = new double[nSlices];
			ch2OverlapCh1 = new double[nSlices];
		}

		public String[] toTextRows(String dataFormat) {
			String[] arrResult = new String[nSlices];
			for (int i = 0; i < nSlices; i++) {
				arrResult[i] = String.format(dataFormat, 
					i1Title + " vs " + i2Title,
					i + 1,
					ch1Title + " vs " + ch2Title, 
					ch1Counts[i], ch2Counts[i], colocCounts[i],
					percCh1NoCh2[i], percCh2NoCh1[i], percColoc[i],
					ch1OverlapCh2[i], ch2OverlapCh1[i]);
			}
			return arrResult;
		}
	}
}



