# coding: utf-8

import os, sys
import pandas as pd
from keras.models import load_model

modelpath = sys.argv[1]
input_pad = [ float(s) for s in sys.argv[2].strip("[]").split(",") ]
input_length = int(sys.argv[3])

print("use model", modelpath)
print("use padded input sequence", input_pad)
print("use tokenized and padded inputsequence", input_length)

# predict with model
input_pad = pd.DataFrame(input_pad).T
mclf_model = load_model(modelpath)

mclf_results_numeric = mclf_model.predict(input_pad)

print("finished prediction")

import json

result = {}
result['output'] = mclf_results_numeric.tolist()[0]
print(json.dumps(result))


