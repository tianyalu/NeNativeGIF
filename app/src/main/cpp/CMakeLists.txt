cmake_minimum_required(VERSION 3.4.1)

aux_source_directory(. src_files)

add_library( # Sets the name of the library.
        native-lib
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        ${src_files})

target_link_libraries( # Specifies the target library.
        native-lib
        # Links the target library to the log library
        # included in the NDK.
        jnigraphics
        log)