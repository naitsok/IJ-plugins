[![version](https://img.shields.io/badge/version-v1.0.5-orange)](https://github.com/naitsok/IJ-plugins/)
[![build](https://img.shields.io/badge/build-passing-green.svg)](https://github.com/naitsok/IJ-plugins/releases/)
[![DOI](https://zenodo.org/badge/350137419.svg)](https://zenodo.org/badge/latestdoi/350137419)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
# Plugins of ImageJ
A collection of plugins for ImageJ developed for image analysis with specific requirements. The plugins are designed to be user-friendly and easy to use in the specialized tasks. When using, please give the [reference](#credits).

## List of plugins

- [RGB Colocalizer](#rgb-colocalizer) ([RGB_Colocalizer.java](./plugins/RGB Workflow/RGB_Colocalizer.java)).
- [RGB Immunoreactivity](#rgb-immunoreactivity) ([RGB_Immunoreactivity.java](./plugins/RGB Workflow/RGB_Immunoreactivity.java)).

[Installation instructions](#requirements-and-installation).


### RGB Colocalizer

RGB_Colocalizer.java plugin performs colocalization analysis in three distinctive ways:
1. Colocalization analysis on images representing separate channels (e.g. red, green, ot blue).
2. Colocalization analysis on one image with all three channels.
3. Colocalization analysis on a directory that contains collection of three channel images.

Depending on the selected analysis type, the plugin will allow choosing between images for separate channels, one three channel image and a directory with three channel images. For all the analysis types, a user will be prompted to put threshold values to distinguish between noise level and signal in the images. For the separate channel analysis or analysis of one three channel images it is possible to calculate thresholds automatically using the plugin functionality.

The main reporting page is the Colocalization statistics for pair of channels, which has the following columns:
- Image titles for colocalization.
- Slice \#. The number of slice in the image.
- Ch1 vs Ch2. Titles of channels, e.g. Red vs Green.
- Ch1 pixels. Number of pixels in channel 1 that are above the threshold and are not colocalized with pixels in channel 2.
- Ch2 pixels. Number of pixels in channel 2 that are above the threshold and not colocalized with pixels in channel 1.
- Coloc pixels. Number of colocalized pixels, i.e. the number of pixel that are in channel 1 and in channel 2 and are above threshold as well.
- Perc Ch1. Percent of pixels that are in channel 1 but not in channel 2 and not colocalized with channel 2 (and above threshold).
- Perc Ch2. Percent of pixels that are in channel 2 but not in channel 1 and not colocalized with channel 1 (and above threshold).
- Perc Coloc. Percent of colocalized pixels, i.e. pixels that are both in channel 1 and in channel 2 normalized to the total number of pixels in both channels and that are above the corresponding thresholds.
- Ch1 Overlap Ch2. Fraction of pixels in channel 1 that are overlaped (colocalized) with channel 2 (i.e. number of colocalized pixels divided by the total number of pixels in channel 1).
- Ch2 Overlap Ch1. Fraction of pixels in channel 2 that are overlaped (colocalized) with channel 1 (i.e. number of colocalized pixels divided by the total number of pixels in channel 2) .

If there are all three channels available, or images are three channel images, additional analysis is performed. This additional analysis performs the colocalization calculation between blue channel and the result of colocalzation for red and green channels. This is done, because blue channel is in the most cases DAPI/Hoechst staining of cell nuclei. Thus, blue channel can be used to quantify the number of cells in the image and normalize the colocalization results to number of cells.

There are several other settings related to result reporting that can be changed:
- Show color coded colocalization plot. This setting plots a 256 x 256 figure for each pair of channels. The plot shows that pixels with such intensity were colocalized in the specified pair of channels. Also  thresholds for each channel are shown. Note: the result shows only if the pixels with specific intensity were colocalized in the channels and does not show the number of the colocalized pixels.
- Show logarithmic intensity colocalization plot. This setting plots a 256 x 256 figure for each pair of channels. The plots shows the number of colocalized pixels with each pixel intensity value in the logarithmic scale. Also thresholds for each channel are shown. This plot is better version of the previous one because it shows the number of colocalized pixels although in logarithmic scale for better visualization.
- Show colocalized image. This setting shows a 8-bit mask with colocalized pixels, i.e. pixels that are found in both channels and are above thresholds.
- Show data for all channels in one row. The plugin calculates colocalization between a pair of channels. When there are all three channels available, there are three pairs of channels for colocalization. The results can be then listed as separate rows for each pair of channels or place all the results into one row (mostly for the ease of analysis in e.g. MS Excel). 

### RGB Immunoreactivity

RGB_Immunoreactivity.java plugin is a handy plugin that combines the functionality of ImageJ to count number of cells and calculate their area. The plugin can work with a single three channel image or images located in a directory. The user is asked to specify the thersholds for each channel and minimum size of cells in pixels. 

For a three channel image or each image in the selected directory, the plugin creates masks for each channel and counts the number of cells and their area (using "Analyze particles..." functionality). 

In case of analysis of a single three channel image, ROI is taken into account, i.e. cells from the ROI are analyzed.

The results are displayed in two different windows: 

1. The standard "Summary" window of "Analyze particles...".
2. The "Immunoreactivity Results" window. Here, each row contains calculated data for all three channels for each image.

## Requirements and Installation
ImageJ 1.50i or higher is required (Java 1.8).

The plugins in this [repository](https://github.com/naitsok/IJ-plugins) are stored as Java (*.java) text files. They must be first compiled to *.class files in order to be recognized by ImageJ. *.class files can be downloaded from the [releases](https://github.com/naitsok/IJ-plugins/releases/) and copied to ImageJ's or Fiji's plugin folder.

### Complile *.java files
To compile *.java files ImageJ 1.50i or higher is required. Compilation of *.java files is not supported by Fiji.
Compilation:
1. Copy into ImajeJ plugin directory.
2. From ImageJ menu, Plugins -> Compile and Run -> select the *.java file (e.g. RGB_Immunoreactivity.java).
3. Restart ImageJ to get the plugin under the Plugins menu.

### Using *.class files

*.class files can be downloaded from [releases](https://github.com/naitsok/IJ-plugins/releases/). Both ImageJ and Fiji recognize the *.class files as installed plugins after they are copied in the plugins folder.


## Credits
The plugins are developed by Mariia Ivanova and Konstantin Tamarov.

Licence: MIT.

When using please give the reference.

To cite the latest version:

M. Ivanova, K. Tamarov. 2021. IJ plugins. [DOI: 10.5281/zenodo.4629678](https://doi.org/10.5281/zenodo.4629678).

To cite the specific version, select the DOI of soecific version on [Zenodo page](https://doi.org/10.5281/zenodo.4629678):

M. Ivanova, K. Tamarov. 2021. IJ plugins. DOI: (Use DOI of specific version).





