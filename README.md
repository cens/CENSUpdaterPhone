# Updater

## Dependencies and Pre-requisites

There are no dependencies or 3rd party jar files in this project.

For the updater to work in fully automated mode without user interaction, superuser has to be setup on the device.

## Build Procedure 

### Manual Build From the UI

Select project in eclipse, clean it and build.

### Command Line Build

#### Prerequisites

This assumes that updater was built manually via UI virst.

build.xml can be generated for any project using this command:

    android update project --target 8 --path .
    # plus for this project, we had to manually change the project name to 'Updater' in build.xml

#### Running the Build

Running the build and sign for all projects:

    # note: replace -v xyz etc. with the correct version
    # assuming TempTrace dosign tool resides in a TempTrace directory parallel to this projec:
    ant release
    ../TempTrace/dosign.sh -p updater -v 119 -s -u


## Program Usage

Updater has two tabs:

* Managed Apps tab shows applications that are currently being managed and autoinstalled
* Status tab shows results recent updated apps checks and installations

Managed apps are color coded like this:

* green color means the application is up-to-date 
* orange color means an application update is available and will be performed soon. 
You will see the orange color only if auto-intall is disabled in settings.
* yellow color means the currently installed app is ahead of what's available
 - This may happen in development mode if the app was installed through a different mechanism other than Updater
* red color means an application update is available, but an attempt to install it failed

Additional fields in application rows provide more information about managed apps:

* last install date (not yet implemented)
* current version
* available version 


