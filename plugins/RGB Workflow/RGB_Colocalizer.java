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
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import ij.plugin.frame.*;
import ij.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.process.*;
import ij.text.*;
import ij.plugin.*;
import ij.plugin.filter.EDM;
import ij.plugin.filter.ParticleAnalyzer;
import ij.measure.*;
import ij.util.Tools;


public class RGB_Colocalizer implements PlugIn, ActionListener, Measurements {
	// Colocalizes pair of channels, plots and shows colocalization images if necessary
	// If all the three channels are available, performs three channel colocalization
	// Three channle colocalization is the colocalization of Blue channel (typically DAPI)
	// and the result of colocalization of Red and Green channels

	// Loading plugin configuration from an external file
	private String configFileName = "RGB_Colocalizer.cfg";
	private File configFile = new File(
		new File(
			new File(
				System.getProperty("user.dir"), 
				"plugins"),
			"RGB Workflow"), 
		configFileName);

	// Subfolder of an image folder to save the results of ananlysis
	private String subFolder = "colocalization_results";

	// Image stacks and images slices
	// private ImageStack iStackR, iStackG, iStackB;
	private ImagePlus iR, iG, iB;
	
	// Titles of images
	private String[] titles;

	// If true displays color coded colocalization plot
	private String configColorColoc = "show_color_coded_colocalization_plot";
	private boolean showColorColoc = false;

	// If true displays intensity coded colocolization plot in logarithmic scale
	private String configIntensityColoc = "show_intensity_coded_colocalization_plot";
	private boolean showIntensityColoc = false;

	// If true displays all the channels in one row
	// If false, channels go in rows one after another
	private String configChannelsInOneRow = "show_channels_in_one_row";
	private boolean showChannelsInOneRow = true;

	// If true displays colocalized image
	private String configColocImage = "show_colocalized_image";
	private boolean showColocImage = false;

	// Pearson correlation with or without pixels under threshold
	private String configPearsonThres = "skip_pixels_below_threshold_for_Pearson_correlasion";
	private boolean skipPearsonBelowThres = false;

	// Dialog to select experiment type: analyze channels separately, analyze one 3 channel image, or analyze directory with 3 channel images
	private GenericDialog gdChooseAnalysis;
	private Button btnSeparateChannels;
	private Button btnOne3Channel;
	private Button btnDir3Channels;

	// Dialog for the selected experiment
	private GenericDialog gdAnalysis;

	// Thresholds
	private String configRedThres = "red_threshold", configGreenThres = "green_threshold", configBlueThres = "blue_threshold";
	private int redThres = 75, greenThres = 75, blueThres = 75;
	private Button btnAutoThresSeparateChannels;
	private Button btnAutoThresOne3Channel;
	private TextField tfRedThres, tfGreenThres, tfBlueThres;

	// Analyze particles in each channel to e.g. mask cell nuclei
	// If each channel needs to be transferred to mask
	// using analyze particles function
	private boolean redAnalPart = false, greenAnalPart = false, blueAnalPart = true;
	// config string to contain these properties 
	private String configRedAnalPart = "red_analyze_particles";
	private String configGreenAnalPart = "green_analyze_particles";
	private String configBlueAnalPart = "blue_analyze_particles";
	// minimum sizes in pixel for analyze particles
	private String redPartSize = "100-Infinity", greenPartSize = "100-Infinity", bluePartSize = "100-Infinity";
	private String configRedPartSize = "red_particle_size";
	private String configGreenPartSize = "green_particle_size";
	private String configBluePartSize = "blue_particle_size";
	private TextField tfRedPartSize, tfGreenPartSize, tfBluePartSize;
	// Circularities of the particles
	private String redPartCirc = "0.00-1.00", greenPartCirc = "0.00-1.00", bluePartCirc = "0.00-1.00";
	private String configRedPartCirc = "red_particle_circularity";
	private String configGreenPartCirc = "green_particle_circularity";
	private String configBluePartCirc = "blue_particle_circularity";
	private TextField tfRedPartCirc, tfGreenPartCirc, tfBluePartCirc;

	// Colors for plotting
	private static int WHITE = 0xFFFFFF;
	private static int RED = 0xFF0000;
	private static int GREEN = 0x00FF00;
	private static int BLUE = 0x0000FF;
	private static int BLACK = 0x000000;
	private static int GRAY = 0x808080;
	private static int ORANGE = 0xFF8000;

	// Results
	private String resultsTitle = "Image titles for colocalization\tSlice #\tCh1 vs Ch2\tCh1 pixels\tCh2 pixels\tColoc pixels\tPercent Ch1\tPercent Ch2\tPercent Coloc\tCh1 Overlap Ch2\tCh2 Overlap Ch1\tPearson\t";
	// private String rgTitle = "%s vs %s\tRed pixels\tGreen pixels\tColoc pixels\tPerc Red\tPerc Green\tPerc Coloc\tRed Overlap Green\tGreen Overlap Red\t";
	// private String rbTitle = "%s vs %s\tRed pixels\tBlue pixels\tColoc pixels\tPerc Red\tPerc Blue\tPerc Coloc\tRed Overlap Blue\tBlue Overlap Red\t";
	// private String gbTitle = "%s vs %s\tGreen pixels\tBlue pixels\tColoc pixels\tPerc Green\tPerc Blue\tPerc Coloc\tGreen Overlap Blue\tBlue Overlap Green\t";
	// private String allChTitle = "%s vs %s\tBlue pixels\tGreen/Red pixels\tColoc pixels\tPerc Blue\tPerc Green/Red\tPerc Coloc\tBlue Overlap Green/Red\tGreen/Red Overlap Blue\t";
	private String dataFormat = "%s\t%d\t%s\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.5f\t%.5f\t%.3f\t";
	private String final2ChResutsTitle = "";
	private ArrayList<String> allLst2ChResults = new ArrayList<String>();
	private ArrayList<String> allLst3ChResults = new ArrayList<String>();

	

	public void run(String arg) {
		// load config if it is available
		readConfig();
		
		// Create and show dialog to select experiment type.
		// IJ.showMessage("My_Plugin", configFile.getPath());
		gdChooseAnalysis = new GenericDialog("RGB Image Colocalizer");
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

		writeConfig();
		return;
	}

	private void readConfig() {
		try {
			FileReader configReader = new FileReader(configFile);
			Properties config = new Properties();
			config.load(configReader);
			showColorColoc = Boolean.parseBoolean(config.getProperty(configColorColoc));
			showIntensityColoc = Boolean.parseBoolean(config.getProperty(configIntensityColoc));
			showChannelsInOneRow = Boolean.parseBoolean(config.getProperty(configChannelsInOneRow));
			showColocImage = Boolean.parseBoolean(config.getProperty(configColocImage));
			redThres = Integer.parseInt(config.getProperty(configRedThres));
			greenThres = Integer.parseInt(config.getProperty(configGreenThres));
			blueThres = Integer.parseInt(config.getProperty(configBlueThres));
			redAnalPart = Boolean.parseBoolean(config.getProperty(configRedAnalPart));
			redPartSize = config.getProperty(configRedPartSize);
			greenAnalPart = Boolean.parseBoolean(config.getProperty(configGreenAnalPart));
			greenPartSize = config.getProperty(configGreenPartSize);
			blueAnalPart = Boolean.parseBoolean(config.getProperty(configBlueAnalPart));
			bluePartSize = config.getProperty(configBluePartSize);
			redPartCirc = config.getProperty(configRedPartCirc);
			greenPartCirc = config.getProperty(configGreenPartCirc);
			bluePartCirc = config.getProperty(configBluePartCirc);
			skipPearsonBelowThres = Boolean.parseBoolean(config.getProperty(configPearsonThres));
			configReader.close();
		}
		catch (FileNotFoundException ex) { /* No config file found, will create a new one automatically. */ }
		catch (IOException ex) { /* Config file could not be read, will rewrite with a new one automatically. */ }
	}

	private void writeConfig() {
		try {
			Properties config = new Properties();
			config.setProperty(configColorColoc, new Boolean(showColorColoc).toString());
			config.setProperty(configIntensityColoc, new Boolean(showIntensityColoc).toString());
			config.setProperty(configChannelsInOneRow, new Boolean(showChannelsInOneRow).toString());
			config.setProperty(configColocImage, new Boolean(showColocImage).toString());
			config.setProperty(configRedThres, new Integer(redThres).toString());
			config.setProperty(configGreenThres, new Integer(greenThres).toString());
			config.setProperty(configBlueThres, new Integer(blueThres).toString());
			config.setProperty(configRedAnalPart, new Boolean(redAnalPart).toString());
			config.setProperty(configRedPartSize, redPartSize);
			config.setProperty(configGreenAnalPart, new Boolean(greenAnalPart).toString());
			config.setProperty(configGreenPartSize, greenPartSize);
			config.setProperty(configBlueAnalPart, new Boolean(blueAnalPart).toString());
			config.setProperty(configBluePartSize, bluePartSize);
			config.setProperty(configRedPartCirc, redPartCirc);
			config.setProperty(configGreenPartCirc, greenPartCirc);
			config.setProperty(configBluePartCirc, bluePartCirc);
			config.setProperty(configPearsonThres, new Boolean(skipPearsonBelowThres).toString());
			FileWriter configWriter = new FileWriter(configFile);
			config.store(configWriter, configFileName);
			configWriter.close();
		}
		catch (IOException ex) {}
	}

	private void analyze() {
		// First do the colocalization for each pair of channels if images are existing
		final2ChResutsTitle = resultsTitle;
		ArrayList<String> lst2ChResults = new ArrayList<String>();
		ColocResult result = null;
		// Date and time of analysis
		LocalDateTime dateTime = LocalDateTime.now();

		// Convert to masks and analyze particles if selected
		if (redAnalPart) {
			iR = analyzeParticles(iR, redThres, redPartSize, redPartCirc);
			// iR.show();
			showColorColoc = false; showIntensityColoc = false; // does not make sense
		}
		if (greenAnalPart) {
			iG = analyzeParticles(iG, greenThres, greenPartSize, greenPartCirc);
			// iG.show();
			showColorColoc = false; showIntensityColoc = false; // does not make sense
		}
		if (blueAnalPart) {
			iB = analyzeParticles(iB, blueThres, bluePartSize, bluePartCirc);
			// iB.show();
			showColorColoc = false; showIntensityColoc = false; // does not make sense
		}

		// Perform colocalization
		if (iR != null && iB != null) {
			result = colocalize(iR, iB, redThres, blueThres, RED, BLUE, "Red", "Blue", 
				redAnalPart, blueAnalPart, redPartSize, bluePartSize, redPartCirc, bluePartCirc);
			lst2ChResults = this.appendResults(lst2ChResults, result.summaryToTextRows(dataFormat), showChannelsInOneRow);
			if (showChannelsInOneRow) final2ChResutsTitle = final2ChResutsTitle + resultsTitle;
			result.saveResult(subFolder, dateTime);
		}
		if (iG != null && iB != null) {
			result = colocalize(iG, iB, greenThres, blueThres, GREEN, BLUE, "Green", "Blue",
				greenAnalPart, blueAnalPart, greenPartSize, bluePartSize, greenPartCirc, bluePartCirc);
			lst2ChResults = this.appendResults(lst2ChResults, result.summaryToTextRows(dataFormat), showChannelsInOneRow);
			if (showChannelsInOneRow) final2ChResutsTitle = final2ChResutsTitle + resultsTitle;
			result.saveResult(subFolder, dateTime);
		}
		if (iR != null && iG != null) {
			result = colocalize(iR, iG, redThres, greenThres, RED, GREEN, "Red", "Green",
				redAnalPart, greenAnalPart, redPartSize, greenPartSize, redPartCirc, greenPartCirc);
			lst2ChResults = this.appendResults(lst2ChResults, result.summaryToTextRows(dataFormat), showChannelsInOneRow);
			result.saveResult(subFolder, dateTime);
		}
		allLst2ChResults.addAll(lst2ChResults);
		
		// now do the blue (DAPI) colocalization with the result of colocalization of red and green channel
		// result variable now contains colocalization of Red and Green channels
		if (iR != null && iG != null && iB != null) {
			showColorColoc = false; showIntensityColoc = false; // does not make sense because Greed/Red coloc image is mask
			ImagePlus iRxB = new ImagePlus("Colocalized_Red_and_Green_channels", result.iCh1vsCh2Stack);
			result = colocalize(iB, iRxB, blueThres, 100, BLUE, ORANGE, "Blue", "Red+Green_colocalized",
				false, blueAnalPart, "Not applicable", bluePartSize, "Not applicable", bluePartCirc);
			result.saveResult(subFolder, dateTime);
		}
		Collections.addAll(allLst3ChResults, result.summaryToTextRows(dataFormat));
		if (iR != null) { iR.changes = false; iR.close(); }
		if (iG != null) { iG.changes = false; iG.close(); }
		if (iB != null) { iB.changes = false; iB.close(); }
	}

	private void showResults() {
		// Colocalization for each pair of channels
		new TextWindow("Colocalization statisitics for pair of channels",
		final2ChResutsTitle, 
		String.join("\n", allLst2ChResults), 
		1500, 900);

		// Colocalization for blue (nuclei) vs colocalized Red and Green
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
		gdAnalysis.setLayout(new GridLayout(10, 4, 5, 5));

		gdAnalysis.addChoice("Red Channel: ", titles, titles[1]);
		gdAnalysis.add(new Label("Red threshold (0-255):", Label.LEFT));
		tfRedThres = new TextField("" + redThres, 4);
		gdAnalysis.add(tfRedThres);

		gdAnalysis.addChoice("Green Channel: ", titles, titles.length > 2 ? titles[2] : titles[0]);
		gdAnalysis.add(new Label("Green threshold (0-255):", Label.LEFT));
		tfGreenThres = new TextField("" + greenThres, 4);
		gdAnalysis.add(tfGreenThres);

		gdAnalysis.addChoice("Blue Channel: ", titles, titles.length > 3 ? titles[3] : titles[0]);
		gdAnalysis.add(new Label("Blue threshold (0-255):", Label.LEFT));
		tfBlueThres = new TextField("" + blueThres, 4);
		gdAnalysis.add(tfBlueThres);	

		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		btnAutoThresSeparateChannels = new Button("Auto threshold images");
        btnAutoThresSeparateChannels.addActionListener(this);
        gdAnalysis.add(btnAutoThresSeparateChannels);

		// Analyze particles
		gdAnalysis.addCheckbox("Red analyze particles, sizes (pixels^2):", redAnalPart);
		tfRedPartSize = new TextField(redPartSize, 4);
		gdAnalysis.add(tfRedPartSize);
		gdAnalysis.add(new Label("Red circularity (0.00-1.00)"));
		tfRedPartCirc = new TextField(redPartCirc, 2);
		gdAnalysis.add(tfRedPartCirc);

		gdAnalysis.addCheckbox("Green analyze particles, sizes (pixels^2):", greenAnalPart);
		tfGreenPartSize = new TextField(greenPartSize, 4);
		gdAnalysis.add(tfGreenPartSize);
		gdAnalysis.add(new Label("Green circularity (0.00-1.00)"));
		tfGreenPartCirc = new TextField(greenPartCirc, 2);
		gdAnalysis.add(tfGreenPartCirc);

		gdAnalysis.addCheckbox("Blue analyze particles, size (pixels^2):", blueAnalPart);
		tfBluePartSize = new TextField(bluePartSize, 4);
		gdAnalysis.add(tfBluePartSize);
		gdAnalysis.add(new Label("Blue circularity (0.00-1.00)"));
		tfBluePartCirc = new TextField(bluePartCirc, 2);
		gdAnalysis.add(tfBluePartCirc);
		
		// Settings
		gdAnalysis.add(new Label("General settings"));
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Skip pixels below thresholds for Pearson correlation", skipPearsonBelowThres);
		gdAnalysis.addCheckbox("Show color coded colocalization plot", showColorColoc); // NOTE: this plot is not reflecting true colocolization percentages for now
		gdAnalysis.addCheckbox("Show logarithmic intensity colocalization plot", showIntensityColoc);
		gdAnalysis.addCheckbox("Show colocalized image", showColocImage);
		gdAnalysis.addCheckbox("Show data for all channels in one row", showChannelsInOneRow);
		

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
			return false;

		// Get images and their titles
		// String tempTitle = "";
		int idxR = gdAnalysis.getNextChoiceIndex();
		if (idxR > 0) { iR = to8bitChannel(WindowManager.getImage(wList[idxR - 1]).duplicate(), 0); } else { iR = null; }
		int idxG = gdAnalysis.getNextChoiceIndex();
		if (idxG > 0) { iG = to8bitChannel(WindowManager.getImage(wList[idxG - 1]).duplicate(), 1); } else { iG = null; }
		int idxB = gdAnalysis.getNextChoiceIndex();
		if (idxB > 0) { iB = to8bitChannel(WindowManager.getImage(wList[idxB - 1]).duplicate(), 2); } else { iB = null; }

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());

		// Get analyze paricles sizes
		redPartSize = tfRedPartSize.getText();
		greenPartSize = tfGreenPartSize.getText();
		bluePartSize = tfBluePartSize.getText();

		redPartCirc = tfRedPartCirc.getText();
		greenPartCirc = tfGreenPartCirc.getText();
		bluePartCirc = tfBluePartCirc.getText();

		// Get boolean values
		redAnalPart = gdAnalysis.getNextBoolean();
		greenAnalPart = gdAnalysis.getNextBoolean();
		blueAnalPart = gdAnalysis.getNextBoolean();
		skipPearsonBelowThres = gdAnalysis.getNextBoolean();
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
		gdAnalysis.setLayout(new GridLayout(10, 4, 5, 5));

		gdAnalysis.addChoice("3 channel image: ", titles, titles[0]);
		gdAnalysis.add(new Label("Red threshold (0-255):", Label.LEFT));
		tfRedThres = new TextField("" + redThres, 4);
		gdAnalysis.add(tfRedThres);

		gdAnalysis.add(new Label("")); gdAnalysis.add(new Label("")); // placeholder labels
		gdAnalysis.add(new Label("Green threshold (0-255):", Label.LEFT));
		tfGreenThres = new TextField("" + greenThres, 4);
		gdAnalysis.add(tfGreenThres);

		gdAnalysis.add(new Label("")); gdAnalysis.add(new Label("")); // placeholder labels
		gdAnalysis.add(new Label("Blue threshold (0-255):", Label.LEFT));
		tfBlueThres = new TextField("" + blueThres, 4);
		gdAnalysis.add(tfBlueThres);	

		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		btnAutoThresOne3Channel = new Button("Auto threshold image");
        btnAutoThresOne3Channel.addActionListener(this);
        gdAnalysis.add(btnAutoThresOne3Channel);

		// Analyze particles
		gdAnalysis.addCheckbox("Red analyze particles, sizes (pixels^2):", redAnalPart);
		tfRedPartSize = new TextField(redPartSize, 4);
		gdAnalysis.add(tfRedPartSize);
		gdAnalysis.add(new Label("Red circularity (0.00-1.00)"));
		tfRedPartCirc = new TextField(redPartCirc, 2);
		gdAnalysis.add(tfRedPartCirc);

		gdAnalysis.addCheckbox("Green analyze particles, sizes (pixels^2):", greenAnalPart);
		tfGreenPartSize = new TextField(greenPartSize, 4);
		gdAnalysis.add(tfGreenPartSize);
		gdAnalysis.add(new Label("Green circularity (0.00-1.00)"));
		tfGreenPartCirc = new TextField(greenPartCirc, 2);
		gdAnalysis.add(tfGreenPartCirc);

		gdAnalysis.addCheckbox("Blue analyze particles, size (pixels^2):", blueAnalPart);
		tfBluePartSize = new TextField(bluePartSize, 4);
		gdAnalysis.add(tfBluePartSize);
		gdAnalysis.add(new Label("Blue circularity (0.00-1.00)"));
		tfBluePartCirc = new TextField(bluePartCirc, 2);
		gdAnalysis.add(tfBluePartCirc);
		
		// Settings
		gdAnalysis.add(new Label("General settings"));
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Skip pixels below thresholds for Pearson correlation", skipPearsonBelowThres);
		gdAnalysis.addCheckbox("Show color coded colocalization plot", showColorColoc); // NOTE: this plot is not reflecting true colocolization percentages for now
		gdAnalysis.addCheckbox("Show logarithmic intensity colocalization plot", showIntensityColoc);
		gdAnalysis.addCheckbox("Show colocalized image", showColocImage);
		gdAnalysis.addCheckbox("Show data for all channels in one row", showChannelsInOneRow);
		

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
			return false;

		// Get images and their titles
		// String tempTitle = "";
		int idxImage = gdAnalysis.getNextChoiceIndex();
		iR = to8bitChannel(WindowManager.getImage(wList[idxImage]), 0);
		iG = to8bitChannel(WindowManager.getImage(wList[idxImage]), 1);
		iB = to8bitChannel(WindowManager.getImage(wList[idxImage]), 2);

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());

		// Get analyze paricles sizes
		redPartSize = tfRedPartSize.getText();
		greenPartSize = tfGreenPartSize.getText();
		bluePartSize = tfBluePartSize.getText();

		redPartCirc = tfRedPartCirc.getText();
		greenPartCirc = tfGreenPartCirc.getText();
		bluePartCirc = tfBluePartCirc.getText();

		// Get boolean values
		redAnalPart = gdAnalysis.getNextBoolean();
		greenAnalPart = gdAnalysis.getNextBoolean();
		blueAnalPart = gdAnalysis.getNextBoolean();
		skipPearsonBelowThres = gdAnalysis.getNextBoolean();
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
				File imgFile = new File(dir, files[i]);
				if (imgFile.isFile()) {
					img = IJ.openImage(imgFile.getPath());
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
			}
			showResults();
		}
	}

	private boolean showDialogDir3ChannelImage() {

		// Creating dialog to select thresholds and settings
		gdAnalysis = new GenericDialog("RGB Image Correlator");
		gdAnalysis.setLayout(new GridLayout(9, 4, 5, 5));

		gdAnalysis.add(new Label("Red threshold (0-255):", Label.LEFT));
		tfRedThres = new TextField("" + redThres, 4);
		gdAnalysis.add(tfRedThres);
		gdAnalysis.add(new Label("")); gdAnalysis.add(new Label("")); // placeholder labels
		
		gdAnalysis.add(new Label("Green threshold (0-255):", Label.LEFT));
		tfGreenThres = new TextField("" + greenThres, 4);
		gdAnalysis.add(tfGreenThres);
		gdAnalysis.add(new Label("")); gdAnalysis.add(new Label("")); // placeholder labels


		gdAnalysis.add(new Label("Blue threshold (0-255):", Label.LEFT));
		tfBlueThres = new TextField("" + blueThres, 4);
		gdAnalysis.add(tfBlueThres);
		gdAnalysis.add(new Label("")); gdAnalysis.add(new Label("")); // placeholder labels

		// Analyze particles
		gdAnalysis.addCheckbox("Red analyze particles, sizes (pixels^2):", redAnalPart);
		tfRedPartSize = new TextField(redPartSize, 4);
		gdAnalysis.add(tfRedPartSize);
		gdAnalysis.add(new Label("Red circularity (0.00-1.00)"));
		tfRedPartCirc = new TextField(redPartCirc, 2);
		gdAnalysis.add(tfRedPartCirc);

		gdAnalysis.addCheckbox("Green analyze particles, sizes (pixels^2):", greenAnalPart);
		tfGreenPartSize = new TextField(greenPartSize, 4);
		gdAnalysis.add(tfGreenPartSize);
		gdAnalysis.add(new Label("Green circularity (0.00-1.00)"));
		tfGreenPartCirc = new TextField(greenPartCirc, 2);
		gdAnalysis.add(tfGreenPartCirc);

		gdAnalysis.addCheckbox("Blue analyze particles, size (pixels^2):", blueAnalPart);
		tfBluePartSize = new TextField(bluePartSize, 4);
		gdAnalysis.add(tfBluePartSize);
		gdAnalysis.add(new Label("Blue circularity (0.00-1.00)"));
		tfBluePartCirc = new TextField(bluePartCirc, 2);
		gdAnalysis.add(tfBluePartCirc);
		
		// Settings
		gdAnalysis.add(new Label("General settings"));
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.add(new Label("")); // placeholder label
		gdAnalysis.addCheckbox("Skip pixels below thresholds for Pearson correlation", skipPearsonBelowThres);
		gdAnalysis.addCheckbox("Show color coded colocalization plot", showColorColoc); // NOTE: this plot is not reflecting true colocolization percentages for now
		gdAnalysis.addCheckbox("Show logarithmic intensity colocalization plot", showIntensityColoc);
		gdAnalysis.addCheckbox("Show colocalized image", showColocImage);
		gdAnalysis.addCheckbox("Show data for all channels in one row", showChannelsInOneRow);

		gdAnalysis.showDialog();

		if (gdAnalysis.wasCanceled())
			return false;

		// Get thresholds
		redThres = Integer.parseInt(tfRedThres.getText());
		greenThres = Integer.parseInt(tfGreenThres.getText());
		blueThres = Integer.parseInt(tfBlueThres.getText());

		// Get analyze paricles sizes
		redPartSize = tfRedPartSize.getText();
		greenPartSize = tfGreenPartSize.getText();
		bluePartSize = tfBluePartSize.getText();

		redPartCirc = tfRedPartCirc.getText();
		greenPartCirc = tfGreenPartCirc.getText();
		bluePartCirc = tfBluePartCirc.getText();

		// Get boolean values
		redAnalPart = gdAnalysis.getNextBoolean();
		greenAnalPart = gdAnalysis.getNextBoolean();
		blueAnalPart = gdAnalysis.getNextBoolean();
		skipPearsonBelowThres = gdAnalysis.getNextBoolean();
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
		String ch1Title, String ch2Title,
		boolean ch1AnalPart, boolean ch2AnalPart,
		String ch1PartSize, String ch2PartSize,
		String ch1PartCirc, String ch2PartCirc) {

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
		
		ColocResult result = new ColocResult(
			img1, img2, ch1Title, ch2Title, thres1, thres2, 
			ch1AnalPart, ch2AnalPart, ch1PartSize, ch2PartSize,
			ch1PartCirc, ch2PartCirc, stackSize);
		
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
			// variables for Pearson correlation
			double sum_x = 0, sum_y = 0, x_sq = 0, y_sq = 0, xy = 0, num_pixels = 0;
			
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
						// Do not count pixels for Pearson correlation below threshold
						if (skipPearsonBelowThres) {
							sum_x -= (double)z1;
							sum_y -= (double)z2;
							x_sq -= (double)z1 * z1;
							y_sq -= (double)z2 * z2;
							xy -= (double)z1 * z2;
							num_pixels -= 1;
						}
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
					// Pearson correlation calculation
					sum_x += (double)z1;
					sum_y += (double)z2;
					x_sq += (double)z1 * z1;
					y_sq += (double)z2 * z2;
					xy += (double)z1 * z2;
				}
			}
			// Calculate Pearson correlation for slice i
			num_pixels += (double)width * height;
			result.corrPearson[i - 1] = ((num_pixels * xy) - (sum_x * sum_y)) / 
				Math.sqrt((num_pixels * x_sq - sum_x * sum_x) * (num_pixels * y_sq - sum_y * sum_y));

			// Create intensity plot from colocCounts and using maxCount
			for (int x = 0; x < 256; x++) { // Loop through Y coordinate
                		for (int y = 0; y < 256; y++) {
					// Make intensity plot in log scale for better visualization
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
			result.colocStack.addSlice("Correlation plot for slice " + i, colocCounts);
			result.colocColorStack.addSlice("Correlation color plot for slice " + i, colocColorCode);
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
			new ImagePlus("Color correlation plot for " + ch1Title + " vs " + ch2Title + " channels", result.colocColorStack).show();
		if (showIntensityColoc)
			new ImagePlus("Intesity correlation plot for " + ch1Title + " vs " + ch2Title + " channels", result.intensityStack).show();
		if (showColocImage)
			new ImagePlus("Colocalization image for " + ch1Title + " vs " + ch2Title + " channels", result.iCh1vsCh2Stack).show();

		return result;
	}

	private ImagePlus analyzeParticles(ImagePlus img, int thres, String partSize, String partCirc) {
		FileInfo fi = img.getOriginalFileInfo();
		String title = img.getTitle();
		IJ.setThreshold(img, thres, 255);
		IJ.run(img, "Convert to Mask", "");

		double minSize = Double.parseDouble(partSize.split("-")[0].trim());
		double maxSize = Double.parseDouble(partSize.split("-")[1].trim());
		double minCirc = Double.parseDouble(partCirc.split("-")[0].trim());
		double maxCirc = Double.parseDouble(partCirc.split("-")[1].trim());
		ParticleAnalyzer partAnal = new ParticleAnalyzer(
		 	ParticleAnalyzer.INCLUDE_HOLES + ParticleAnalyzer.SHOW_MASKS, 0, 
		 	new ResultsTable(), minSize, maxSize, minCirc, maxCirc);
		partAnal.analyze(img);
		img = partAnal.getOutputImage();

		IJ.run(img, "Watershed", "");
		IJ.run(img, "Invert LUT", "");

		img.setTitle(title);
		img.setFileInfo(fi);
		
		return img;
	}

	private int getIntensityColor(double ratio) {
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

	private int getIntensityColor2(double ratio) {
		int red = (int) (255 * ratio);
		int green = (int) (255 * ratio);
		int blue = (int) (255 * ratio);

		int rgb = (red << 16 | green << 8 | blue);
		return rgb;
	}

	private int getAutoThres(Choice choice, int channel, TextField tf) {
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
		FileInfo imageInfo = image.getOriginalFileInfo();
		if (image.getType() == ImagePlus.COLOR_RGB) {
			image = ChannelSplitter.split(image)[channel];
			image.setTitle(tempTitle);
			image.setFileInfo(imageInfo);
			return image;
		}
		
		if (image.getType() != ImagePlus.GRAY8) {
			ImageConverter ic = new ImageConverter(image);
			ic.convertToGray8();
			image.setFileInfo(imageInfo);
			return image;
		}

		return image;
	}
	
	class ColocResult {
		// Keeps all the colocalization results for pair of images
		
		// Titles of colocalized images
		public String i1Title, i2Title;
		// Get paths of images to be colocalized
		public String i1Path = "", i2Path = "";
		// Titles of channels to colocalize
		public String ch1Title, ch2Title;
		// Thresholds for channels to colocalize
		public int ch1Thres, ch2Thres;
		// Analyze particles
		public boolean ch1AnalPart, ch2AnalPart;
		public String ch1PartSize, ch2PartSize;
		public String ch1PartCirc, ch2PartCirc;
		// Image with colocalization
		public ImageStack iCh1vsCh2Stack;
		// Stack plots to keep correlation plots for each slice
		// The coordinates are 0-255 because of color intensity
		public ImageStack colocStack;
		// Color codes for stack plot
		public ImageStack colocColorStack;
		// Intensity stack plot for each slice
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
		// Pearson correlation coefficient for each slice
		public double[] corrPearson;

		public ColocResult(ImagePlus img1, ImagePlus img2,
			String chan1Title, String chan2Title,
			int chan1Thres, int chan2Thres,
			boolean chan1AP, boolean chan2AP,
			String chan1PS, String chan2PS,
			String chan1PC, String chan2PC,
			int numSlices) {
			// Creates an empty ColocResults for two images specified by titles
			// numSlices is number of slices in the images
			i1Title = img1.getTitle();
			i2Title = img2.getTitle();
			FileInfo fileInfo = null;
			fileInfo = img1.getOriginalFileInfo();
			if (fileInfo != null) {
				i1Path = fileInfo.getFilePath();
			}
			fileInfo = img2.getOriginalFileInfo();
			if (fileInfo != null) {
				i2Path = fileInfo.getFilePath();
			}
			ch1Title = chan1Title;
			ch2Title = chan2Title;
			ch1Thres = chan1Thres;
			ch2Thres = chan2Thres;

			// Analyze particles
			ch1AnalPart = chan1AP;
			ch2AnalPart = chan2AP;
			ch1PartSize = chan1PS;
			ch2PartSize = chan2PS;
			ch1PartCirc = chan1PC;
			ch2PartCirc = chan2PC;
			
			// Create the image stack for colocalized image
			ImageStack img1Stack = img1.getStack();
			iCh1vsCh2Stack = new ImageStack(img1Stack.getWidth(), img1Stack.getHeight());

			// Stack plots to keep correlation plots for each slice
			// The coordinates are 0-255 because of color intensity
			colocStack = new ImageStack(256, 256);
			// Color codes for stack plot
			colocColorStack = new ImageStack(256, 256);
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
			corrPearson = new double[nSlices];
		}

		public String[] summaryToTextRows(String dataFormat) {
			String[] arrResult = new String[nSlices];
			for (int i = 0; i < nSlices; i++) {
				arrResult[i] = String.format(dataFormat, 
					i1Title + " vs " + i2Title,
					i + 1,
					ch1Title + " vs " + ch2Title, 
					ch1Counts[i], ch2Counts[i], colocCounts[i],
					percCh1NoCh2[i], percCh2NoCh1[i], percColoc[i],
					ch1OverlapCh2[i], ch2OverlapCh1[i], corrPearson[i]);
			}
			return arrResult;
		}

		public StringBuilder colocStackToSBuilder() {
			StringBuilder colocStackSB = new StringBuilder();
			colocStackSB.append("Colocalization matrix for X:" + i1Title + " vs Y:" + i2Title +
			" and " + ch1Title + " vs " + ch2Title + "\n");
			for (int i = 1; i <= nSlices; i++) {
				colocStackSB.append(String.format("Slice %d\n", i));
				// first data row is the x axis for the pixel values) 0, 1, ..., 255
				// Here the length is 257 because the first column is Y axis
				String[] dataRow = new String[257];
				dataRow[0] = "";
				for (int x = 0; x < 256; x++) {
					dataRow[x + 1] = new Integer(x).toString();
				}
				colocStackSB.append(String.join("\t", dataRow) + "\n");
				// Now go through the slice but adding
				for (int y = 0; y < 256; y++) {
					dataRow = new String[257];
					dataRow[0] = new Integer(y).toString();
					for (int x = 0; x < 256; x++) {
						dataRow[x + 1] = new Float((int)colocStack.getProcessor(i).getf(y, 255 - x) + 1).toString();
					}
					colocStackSB.append(String.join("\t", dataRow) + "\n");
				}
				colocStackSB.append("\n");

			}
			return colocStackSB;
		}

		public void saveResult(String analysisFolderName, LocalDateTime dateTime) {
			// Save analysis metadata
			StringBuilder metadata = new StringBuilder();
			metadata.append("Image 1 information\n");
			metadata.append("Name:\t" + i1Title + "\n");
			metadata.append("Path:\t" + i1Path + "\n");
			metadata.append("Channel:\t" + ch1Title + "\n");
			metadata.append("Threshold:\t" + new Integer(ch1Thres).toString() + "\n");
			metadata.append("Analyze particles:\t" + new Boolean(ch1AnalPart).toString() + "\n");
			metadata.append("Particle size (pixels^2):\t" + ch1PartSize + "\n");
			metadata.append("Particle circularity:\t" + ch1PartCirc + "\n");
			metadata.append("\n");
			metadata.append("Image 2 information\n");
			metadata.append("Name:\t" + i2Title + "\n");
			metadata.append("Path:\t" + i2Path + "\n");
			metadata.append("Channel:\t" + ch2Title + "\n");
			metadata.append("Threshold:\t" + new Integer(ch2Thres).toString() + "\n");
			metadata.append("Analyze particles used:\t" + new Boolean(ch2AnalPart).toString() +  "\n");
			metadata.append("Particle size (pixels^2):\t" + ch2PartSize + "\n");
			metadata.append("Particle circularity:\t" + ch2PartCirc + "\n");
			metadata.append("Pearson correlation:\t");
			for (int i = 0; i < nSlices; i++) {
				metadata.append(new Double(corrPearson[i]).toString() + "\t");
			}
			metadata.append("\n");

			// Save colocalization matrix
			StringBuilder colocMatrix = colocStackToSBuilder();

			String metadataFileName = "Metadata_" + i1Title + "_vs_" + i2Title + "__" + ch1Title + "_vs_" +ch2Title + ".txt";
			String matrixFileName = "Matrix_" + i1Title + "_vs_" + i2Title + "__" + ch1Title + "_vs_" +ch2Title + ".txt";

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			String dateFolder = formatter.format(dateTime);
			formatter = DateTimeFormatter.ofPattern("HH-mm-ss");
			String timeFolder = formatter.format(dateTime);
			// Save result in the subdirectory where the image or directroy with 
			// images is located. If images are located in the different folders
			// then saves the reults in both of the folders.
			if (i1Path != null && i1Path.length() > 0) {
				File analysisFolder1 = new File(new File(i1Path).getParent(), analysisFolderName);
				analysisFolder1 = new File(new File(analysisFolder1, dateFolder), timeFolder);
				if (!analysisFolder1.exists()) {
					analysisFolder1.mkdirs();
				}
				try {
					OutputStreamWriter fileToWrite = new OutputStreamWriter(new FileOutputStream(new File(analysisFolder1, metadataFileName)), StandardCharsets.UTF_8);
					fileToWrite.write(metadata.toString());
					fileToWrite.close();
					fileToWrite =  new OutputStreamWriter(new FileOutputStream(new File(analysisFolder1, matrixFileName)), StandardCharsets.UTF_8);
					fileToWrite.write(colocMatrix.toString());
					fileToWrite.close();
				} catch (IOException ex) {
					IJ.showMessage("Error!", ex.getMessage());
				}
			}
			if (i2Path != null && i2Path.length() > 0) {
				File analysisFolder2 = new File(new File(i2Path).getParent(), analysisFolderName);
				analysisFolder2 = new File(new File(analysisFolder2, dateFolder), timeFolder);
				if (!analysisFolder2.exists()) {
					analysisFolder2.mkdirs();
				}
				try {
					OutputStreamWriter fileToWrite =  new OutputStreamWriter(new FileOutputStream(new File(analysisFolder2, metadataFileName)), StandardCharsets.UTF_8);
					fileToWrite.write(metadata.toString());
					fileToWrite.close();
					fileToWrite =  new OutputStreamWriter(new FileOutputStream(new File(analysisFolder2, matrixFileName)), StandardCharsets.UTF_8);
					fileToWrite.write(colocMatrix.toString());
					fileToWrite.close();
				} catch (IOException ex) {
					IJ.showMessage("Error!", ex.getMessage());
				}
			}
		}
	}
}



