# ACoLi Glosser

Command-line tools for doing morphological segmentation and morphological glossing. Developed within the project "Linked Open Dictionaries", funded by the BMBF, for applications within the humanities.

Note that a requirement in the development was to provide a transparent, knowledge-based and flexible approach. We thus provide a dictionary-based (rather than neuronal) implementation. Beyond mere lookup, we provide an inference mechanism to annotate unseen words, based on frequency and overlap with reference annotations. This is less precise than manual annotation with IGT tools such as FLEx or Toolbox, but less work-intense. The glosser is particularly well-suited for historical languages with defective or unsystematic orthographies (where grammatically relevant phonemes cannot be reliably identified under all circumstances) as it does not require for formalize the mapping from orthography to morphological segmentation, but just exploits existing analyses.

An example for such a defective orthography is Sumerian. Below a manual annotation where morphemes that have been inferred by the annotator but that are not directly recognizable from the surface string (marked in square brackets):

    2(u)    2(u)[ten]       NU      _       _       _
    sze     sze[barley]     N       _       _       _
    gur     gur[unit]       N       _       _       _
    lugal   lugal[king][-ak][-ø]    N.GEN.ABS       _       _       _

For those cases, the most probable analysis will be returned (most frequent known analysis, maximum overlap [+frequency] for unknown analyses).

Sample call:

> cat MY-INPUT.conll | python3 glosser.py MY-DICT.tsv > MY-ANNOTATION.conll

Input files must be provided in a one-word-per-line tab-separated format (TSV, i.e., CSV with TAB separators). The first column must contain the word. following columns are copied into the output without modifications.
Lines starting with # are treated as comments and will be copied into the output, but not modified.

The dictionary must be provided in a one-word-per-line tab-separated format. The first column must contain the word, the second must contain the gloss, the third (optional) column can contain frequencies. If no frequency is provided, we assume frequency=1. Following columns will be ignored. It is possible to provide several dictionaries, if the same word occurs multiple times, its frequencies are added. 

The output is a one-word-per-line tab-separated format which contains a complete copy of the input, with several columns added:
- BASE baseline (the first annotation found in the dictionary, _ if no match is found)
- DICT lookup-based annotation (the most frequent annotation found in the dictionary, _ if no match is found)
- CODE strategy for inferring annotations for unseen words, cf. PRED column below

            D  dictionary match
            I  unseen word, inference strategies in order of application:
             a left and right match produce the same gloss(es)
             b right starts with left or left ends with right;*
             c right contains left => right minus everthing after left OR
               left contains right => left minus everything before right*
             d left ends with the begin of right => concatenate;*
             e right starts with left or left ends with with right;
             f dictionary gloss that starts with left and ends with right;
             g dictionary gloss that starts with the beginning of left and ends with the end of right;**
             h left or right found as dictionary gloss;***
             i (begin of) left or (end of) right found as dictionary gloss;
             j dictionary glosses beginning with left or ending with right;
                notes: *   >1 characters match,
                       **  >2 characters match,
                       *** dictionary frequency >1 to prevent overspecific outliers
                CODE can be used for debugging, but also for assessing prediction quality

- LEFT most probable left-to-right dictionary annotation(s) for unseen words
- RIGHT most probable right-to-left dictionary annotations (= longest form match)
- PRED predicted annotation retrieved from DICT, LEFT and RIGHT (cf. CODE)
                selection criteria: most frequent glosses/form > most frequent glosses > shortest glosses

Note that it is possible to concatenate subsequent calls to the glosser, e.g., to provide morphological segmentation first, and then morphological glosses. For these, only the dictionary must be exchanged. Each glosser adds its own set of columns.

> cat MY-INPUT.conll | python3 glosser.py MY-DICT1.tsv | python3 glosser.py MY-DICT2.tsv > MY-ANNOTATION.conll

For filtering the output of the glosser, we recommend using Unix command line tools such as cut. For *any* data set, the following call returns the form column together with the predicted annotation, ignoring everything else.

> cat MY-INPUT.conll | cut -f 1 | python3 glosser.py MY-DICT.tsv | cut -f 1,7 > MY-ANNOTATION.conll

The first call to cut eliminates existing annotations other than the string itself, the second call to cut eliminates the unnecessary Glosser columns.
