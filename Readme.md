Unfortunately Google Takeout is not very useful when it comes to trying to pull all your photos out and put
it into another system, such as Synology. When you download the archives from Google Takeout and unzip them
you are left with files in random folders and seperate json files containing the metadata.

My goal is to make it possible to create a sane structure of all that before I move it over to Synology.

To use this you need to do the following.
1. Download all your takeout files.
2. Unzip all of them -- `find . -name "*.zip" -exec unzip {} \;`
3. Run the program 

This can be done either by eidting and running the AppRunnerTest or by running the jar file.

To run via jar file do the following:
1. `mvn clean install`
2. `java -jar target/picture_mover-0.1.jar "source directory" "target directory"`

The program look at all files under the source directory and make a note of the created date in the metadata.
Then for each file it will
1. Create a directory in the target directory with the year and month of the created date. Example `target/2019/1`
2. Copy the file to the directory created in step 1.
3. Set the lastModifiedDate of the file to be the same as the metadata.

If no created date could be found for the file it will place the pictures in `target/unknown`.