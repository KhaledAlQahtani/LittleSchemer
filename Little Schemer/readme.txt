on Ubuntu  virtula machine :
unzip it: unzip inter.zip
build: cd inter ; ant
install Oracle JDK8 
install Ubuntu packages r-base, r-mathlib, libopenblas-base
build glue code for system libraries and GNU-R: cd inter ; ./build.sh
build glue code for native BLAS and LAPACK: cd netlib-java ; ./build.sh
check the glue code can be loaded: cd ../.. ; ./nr.sh should give output
