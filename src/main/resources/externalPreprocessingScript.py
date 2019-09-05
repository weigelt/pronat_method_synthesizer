import os
import IPython.display as ipd
import numpy as np
import pandas as pd
pd.set_option('display.max_colwidth', -1)

import string
punctuations = string.punctuation

def fix_punct(col):
    col = col.apply(lambda x: x.replace('3/4', 'three quarters').replace('/', ' or ')
                    )
    
    col = col.apply(lambda x: x.replace('1.', 'first').replace('2.', 'second').replace('3.', 'third')
                    .replace('4.', 'forth').replace('5.', 'fifth').replace('6.', 'sixth')
                    .replace(' A.', ' ').replace(' B.', ' ').replace(' C.', ' ').replace(' D.', ' ')
                    .replace(' a.', ' ').replace(' b.', ' ').replace(' c.', ' ').replace(' d.', ' ')
                   )
    
    col = col.apply(lambda x: x.replace("That's", 'That is').replace("that's", 'that is')
                    .replace("i'm", 'I am').replace("I'm", 'I am').replace("don't", 'do not')
                    .replace("you've", 'you have').replace("You've", 'you have')
                    .replace("They're", 'they are').replace("they're", 'they are')
                    .replace("Here's", 'here is').replace("here's", 'here is')
                    .replace("it's", 'it is')
                   )
    
    col = col.apply(lambda x: x.replace('”', '').replace('“','').replace('â€˜', '').replace('â€™', '')
                    .replace('â€œ', '').replace('â€','').replace('Â«','').replace('Â»','').replace('','')
                   )
    
    translator = str.maketrans(string.punctuation, ' '*len(string.punctuation)) #map punctuation to space
    col = col.apply(lambda x: x.translate(translator))

    return col
    
def fix_abbreviations(col):
    col = col.apply(lambda x: x.replace(' ll ', ' will ').replace(' re ', ' are ').replace(' ve ', ' have ')
                    .replace('don t', 'do not').replace(' 2 ', ' two ').replace(' 3 ', ' three ')
                    .replace(' 4 ', ' four ').replace(' 5 ', ' five ').replace(' 6 ', ' six ')
                    )
    return col

import spacy
parser = spacy.load("en")

def spacy_tokenizer(sentence):
    parsed = parser(sentence)
    lowered = [tok.text.lower().strip() if tok.lemma_ != "-PRON-" else tok.lower_ for tok in parsed]
    tokens = [tok for tok in lowered if tok not in punctuations]     
    return tokens

def spacy_lemmatizer(sentence):
    parsed = parser(sentence)
    lemma = [tok.lemma_.lower().strip() if tok.lemma_ != "-PRON-" else tok.lower_ for tok in parsed]
    tokens = [tok for tok in lemma if tok not in punctuations] 
#     tokens = [tok for tok in tokens if (tok not in stopwords and tok not in punctuations)] 
    return tokens

# Extract important columns from dataset
dataset = pd.read_csv("study_multi_labeled_notypos_parse.csv",sep=",") 
dataset = dataset.drop("Unnamed: 0", axis=1).drop("Unnamed: 0.1", axis=1)
dataset.sample(3)

df = pd.DataFrame()
df["input"] = dataset["to_tokenize"]
df["output"] = dataset["binary_output"]
df["scenario"] = dataset["scenario"]

df.sample(3)
# Preprocessing

df["input"] = fix_punct(df["input"])        # schon in to_tokenize enthalten 
df["input"] = fix_abbreviations(df["input"])
df["tokenized"] = [spacy_tokenizer(sentence) for sentence in df["input"]]
df["lemmatized"] = [spacy_lemmatizer(sentence) for sentence in df["input"]]
df.sample(3)
