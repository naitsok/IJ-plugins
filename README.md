# Plugins of ImageJ
A collection of plugins for ImageJ developed for image analysis with specific requirements. The plugins are designed to be user-friendly and easy to use in the specialized tasks.

## List of plugins

- [RGB Colocalizer](#rgb-colocalizer) ([RGB_Colocalizer.java](./blob/main/plugins/RGB_Colocalizer.java)).
- [RGB Immunoreactivity](#rgb-immunoreactivity) ([RGB_Immunoreactivity.java](./blob/main/plugins/RGB_Immunoreactivity.java)).

[Installation instructions](#requirements-and-installation).


### RGB Colocalizer

RGB_Colocolizer.java plugin performs colocalization analysis in three distinctive ways:
1. Colocalization analysis on images representing separate channels (e.g. red, green, ot blue).
2. Colocalization analysis on one image with all three channels.
3. Colocalization analysis on a directory that contains collection of three channel images.

Depending on the selected analysis type, the pluging will allow to choose between images for separate channels, one three channel image and a directory with three channel images. For all of the analysis types, a user will be prompted to put threshold values to distinguish between noise level and signal in the images. For the seprate channel analysis or analysis of one three channel images it is possible to calculate thresholds automatically using the plugin functionality.

The main reporting page is the Colocalization statistics for pair of channels, which has the following columns:
- Image titles for colocalization.
- Ch1 pixels: number of pixels in channel 1 that are above the threshold and are not colocalized with pixels in channel 2.
- Ch2 pixels: number of pixels in channel 2 that are above the threshold and not colocalized with pixels in channel 1.
- Coloc pixels: number of colocalized pixels, i.e. the number of pixel that are in channel 1 and in channel 2 and are above threshold as well.
- Perc Ch1: percent of pixels that are in channel 1 but not in channel 2 and not colocalized with channel 2 (and above threshold).
- Perc Ch2: percent of pixels that are in channel 2 but not in channel 1 and not colocalized with channel 1 (and above threshold).
- Perc Coloc: percent of colocalized pixels, i.e. pixels that are both in channel 1 and in channel 2 normalized to the total number of pixels in both channels and that are above the corresponding thresholds.
- Ch1 Overlap Ch2: fraction of pixels in channel 1 that are overlaped (colocalized) with channel 2 (i.e. number of colocalized pixels divided by the total number of pixels in channel 1).
- Ch2 Overlap Ch1: fraction of pixels in channel 2 that are overlaped (colocalized) with channel 1 (i.e. number of colocalized pixels divided by the total number of pixels in channel 2) .

If there are all three channels available, or images are three channel images, additional analysis is performed. This additional analysis performs the colocalization calculation between blue channel and the result of colocalzation for red and green channels. This is done, because blue channel is in the most cases DAPI/Hoechst staining of cell nuclei. Thus, blue channel can be used to quantify the number of cells in the image and normalize the colocalization results to number of cells.

There are several other settings related to result reporting that can be changed:
- Show color coded colocalization plot. This setting plots a 256 x 256 figure for each pair of channels. The plot shows that pixels with such intensity were colocalized in the specified pair of channels. Also  thresholds for each channel are shown. Note: the result shows only if the pixels with specific intensity were colocalized in the channels and does not show the number of the colocalized pixels.
- Show logarithmic intensity colocalization plot. This setting plots a 256 x 256 figure for each pair of channels. The plots shows the number of colocalized pixels with each pixel intensity value in the logarithmic scale. Also thresholds for each channel are shown. This plot is better version of the previous one because it shows the number of colocalized pixels although in logarithmic scale for better visualization.

### RGB Immunoreactivity

## Requirements and Installation

## Credits
The plugins are developed by Mariia Ivanova and Konstantin Tamarov.

Licence: MIT


