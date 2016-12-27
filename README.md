# Building Detector Offline Tools

The package contains two tools bundled together: a visualizer GUI application and a band extractor command line tool.

## Visualizer GUI

The purpose of the visualizer application is to let you view 3-band and 8-band images, view ground truth building footprints and your solution's building footprint as overlays on these images, compare truth to solution and calculate your solution's score.
Download the [visualizer package](https://github.com/SpaceNetChallenge/BuildingDetectorVisualizer/files/674623/visualizer-1.1.zip) and unpack it anywhere on your system. Open a command window in the directory where you unzipped the package and execute

<pre>java -jar visualizer.jar -truth <truth_file> -solution <solution_file>
     -image3-dir <3band_image_directory> -image8-dir <8band_image_directory>
     -band-triplets <band_definition_file>
</pre>

_(The above is a single line command, line breaks are only for readability.)_

This assumes that you have Java (at least v1.7) installed and it is available on your path. The meaning of the above parameters are the following:

*   -truth specifies the location of to the truth file, see the ./data/truth.csv for an example,
*   -solution is your solution file, see ./data/solution.csv,
*   -image3-dir and -image8-dir are the directories containing the 3-band and 8-band imagery, respectively.
*   -band-triplets points to a file that defines the band index triplets used to generate RGB images from the 8-band imagery. See ./data/band-triplets.txt, it describes the required syntax of band triplet definitions.

All file and directory parameters can be relative or absolute paths. The -truth and -solution paramaters are optional, the tool is able to run without them, see -run-mode below.
For example a command line that will run the app with the supplied sample data:

<pre>java -jar visualizer.jar -truth ./data/truth.csv -solution ./data/solution.csv
     -image3-dir ./data/3band -image8-dir ./data/8band
     -band-triplets ./data/band-triplets.txt
</pre>

_(Line breaks are only for readability.)_

There are some other optional command line parameters you can use:

*   -w <width> : Width of the tool's screen. Defaults to 1500.
*   -iou-threshold : Defaults to 0.5.
*   -no-gui: if present then no GUI will be shown, the application just scores the supplied solution file in command line mode.
*   -fp-border <r,g,b,a> : with this you can customize the colour of the border of the polygons representing false positives. The parameter should be 4 integers separated by commas, no spaces in between. E.g to set it to semi-transparent blue you can use: -fp-border 0,0,255,128
*   -fp-fill <r,g,b,a> : similar to the previous for the fill colour of the false positive polygons.
*   -fn-border and -fn-fill: as above for false negatives
*   -tp-border-solution, -tp-fill-solution, -tp-border-truth and -tp-fill-truth : as above for true positives, but here you can set different colours for the truth and solution polygons.
*   -run-mode : one of 'truth', 'solution' or 'image-directory' (without the quotes). Run mode specifies which images are loaded into the tool. If run mode is 'truth' then all images that are listed in the given truth file are loaded. Similarly for 'solution', all images that are present in the solution file are loaded. In 'image-directory' mode all images of the -image3-dir are loaded. Defaults to 'truth'.

All these have proper defaults so you can leave them out.

### Operations

Usage of the tool should be straightforward. Select the view type from the top drop down list: the 3 band image or one of your predefined band triplet combinations. Select the image to be displayed from the bottom drop down list. Note that you can also switch to another image by clicking the line containing an image name in the output log window.
Solution and truth are compared automatically (if both truth and solution files are specified), scores are displayed in the log window and also in the command line.
You can zoom in/out within the image view by the mouse wheel, and pan the view by dragging.

### Sample images

Note that the sample images bundled together with the visualizer tool are not actual satellite images, these are only added for demonstration purposes. The 3-band images are created from aerial photography, the 8-band images are created by image processing manipulation of the 3-band images. (The Red, Green, Blue channels are correct but the Coastal, Near-IR1, etc. channels are fake.) You need to download the training data set of the contest to obtain real, ground-truthed satellite imagery. See the problem statement and [this link](https://aws.amazon.com/public-data-sets/spacenet) for details on how to access real data.

### Examples

1\. Typical usage: compare truth and solution files. Calculate score and show images.

<pre>java -jar visualizer.jar -truth ./data/truth.csv -solution ./data/solution.csv
     -image3-dir ./data/3band -image8-dir ./data/8band
     -band-triplets ./data/band-triplets.txt
</pre>

2\. As above, without GUI, does only the scoring.

<pre>java -jar visualizer.jar -truth ./data/truth.csv -solution ./data/solution.csv
     -image3-dir ./data/3band -image8-dir ./data/8band
     -band-triplets ./data/band-triplets.txt -no-gui
</pre>

3\. Just show all the images in a folder.

<pre>java -jar visualizer.jar -image3-dir ./data/3band -image8-dir ./data/8band
     -band-triplets ./data/band-triplets.txt -run-mode image-directory
</pre>

4\. Show truth data without comparing with solution.

<pre>java -jar visualizer.jar -image3-dir ./data/3band -image8-dir ./data/8band
     -band-triplets ./data/band-triplets.txt -truth ./data/truth.csv
</pre>

5\. Show your solution without truth data.

<pre>java -jar visualizer.jar -image3-dir ./data/3band -image8-dir ./data/8band -band-triplets ./data/band-triplets.txt -solution ./data/solution.csv -run-mode solution
</pre>

## Band extractor CLI

The purpose of the band extractor application is to extract individual bands from 8-band GeoTiff files. Most image viewers and image processing libraries can't handle 8-band images so this application is provided as a file conversion convenience tool.
Open a command window in the directory where you unzipped the package and execute

<pre>java -cp visualizer.jar visualizer.BandExtractor -in <input-file> -out <output-directory>
</pre>

where

*   <input-file> is either a 8-band GeoTiff file or a directory containing such files. In the latter case all files within the directory are processed.
*   <output-directory> is a directory where the extracted images will be placed. The application creates output files having the same name as the input file appended with a -b[i] suffix, i = [1..8].

There are other optional command line parameters you can use:

*   -type : one of 'png', 'tif', 'none' (without the quotes). Specifies the output file type, default is 'png'. If 'none' then no output file is generated, only pixel value statistics are output.
*   -max <max> : an integer value, defaults to -1 meaning no maximum is set. See 'Pixel value scaling' for a description of how this value is used.
*   -bands <band-list> : a string containing a list of integer values from the [1..8] range, meaning the set of bands you wish to extract. Defaults to '12345678' (without the quotes), meaning all bands. Don't use any separator between the numbers.

### Pixel value scaling

The 8-band GeoTiff files contain 16-bit pixel intensity values, these have to be converted to standard 8-bit RGB images. You can specify an external maximum value with the max parameter, all values higher than that will be converted to a 255 grayscale value, values lower than that will be proportionally lower. If you don't specify a maximum value then the file's internal maximum will be used: this is the maximum pixel value found in all the selected bands (which may be a subset of all bands if you specified a <band-list> other than '12345678'). Note that if you don't specify an external maximum then pixel intensities can not be meaningfully compared across images. When the tool runs the maximum pixel values for each band are displayed. If you want your extracted band images to have comparable values then study this output and select an appropriate maximum value that you specify for the conversion. Note that you can select different values for each band if you use both the -max and the -bands parameters during conversion.

# Licenses

The visualizer and band extractor tools use the imageio-ext library for reading multiband TIFF files. The imageio-ext library is LGPL licensed, see [here](https://github.com/geosolutions-it/imageio-ext/blob/master/LICENSE.txt) for its license text. See [here](https://github.com/geosolutions-it/imageio-ext) for details on the library.
The sample images bundled together with the visualizer tool were created using [this](https://commons.wikimedia.org/wiki/File:Ortofoto_Citt%C3%A0_Alta,_Rocca.jpg) image. The file is licensed under the [Creative Commons Attribution-Share Alike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/) license. All imagery bundled with the tool is also licensed under the same license.
