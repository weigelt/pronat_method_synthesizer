#!/usr/bin/env bash

venv_name=MethodSynthesizer_venv

if [ -d "$venv_name" ] ; then
	echo "Virtual environment $venv_name already exists. Installed packages:"
	echo pip freeze --local
	# source ./$venv_name/bin/activate

else
	echo "Missing MethodSynthesizer virtual environment. Create $venv_name."

	python3 -m venv $venv_name
	source ./$venv_name/bin/activate
	pip install pandas
	pip install keras
	pip install tensorflow
	source ./$venv_name/bin/deactivate

fi

echo "Done"