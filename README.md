
### Command Line Build

#### Prerequisites

This assumes that updater was built manually via UI virst.

build.xml can be generated for any project using this command:

    android update project --target 8 --path .
    # plus for this project, we had to manually change the project name to 'Updater' in build.xml

#### Running the Build

Running the build and sign for all projects:

    # note: replace -v xyz etc. by the correct version
    # assuming TempTrace dosign tool in parallel to this projec:
    ../TempTrace/dosign.sh -p updater -v 119 -s -u

