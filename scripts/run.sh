#!/bin/sh
dirs="breakthrough experiments framework mcts"
class=`shift`

CP="."
for d in $dirs
do
  CP="$CP:build/$d"
done

java -Xmx2048m -Xms512m -XX:+UseSerialGC -cp $CP $class $@

# Classes with main 
#
#   amazons.gui.Amazons
#   cannon.gui.CannonGui
#   chinesecheckers.gui.CCGui
#   pentalath.gui.PentalathGui
#   lostcities.Game
#   experiments.AITests
# 


