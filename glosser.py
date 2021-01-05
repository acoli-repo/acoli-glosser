import sys,os,re,argparse
from copy import deepcopy

# derived from Glosser.java
# accuracy is slightly *better* (not clear why, maybe different )
# eval inferred annotations (excl. D!) on ETCSRI test, using ETCSRI train
#       left     right    mrg       total		
# java	59 (15%) 67 (17%) 110 (27%) 406	(100%)
# py    73 (18%) 70 (17%) 112 (28%) 406 (100%)

# note that these scores underrate the overall performance
# - on the one hand, errors are concerned with the innermost morphemes, mostly
#   but the outermost morphemes define their syntactic characteristics
# - on the other hand, the vast majority of the ETCSRI test set is explained by
#   dictionary
# D only (py=java): 1831/2128 (86% accuracy)
# D+I (java):       1941/2534 (77% accuracy)
# D+I (py):         1943/2534 (77% accuracy)

# it is not clear how the results of this evaluation can be extended to other langugages
# as Sumerian is particularly challenging because of a somewhat defective orthography (esp. of morphemes)

args=argparse.ArgumentParser(description=
	"heuristic, dictionary-based glossing with inference capabilities\n\n"+
	"reads one word per line from stdin, most until the first TAB character\n" +
	"writes TAB-separated values to stdout:\n" +
	"\tORIG original TSV columns\n" +
	"\tBASE baseline: returns the first annotation found in DICTs\n" +
	"\tDICT most frequent gloss(es), on dictionary lookup\n" +
	"\tCODE strategies to predict gloss annotation:\n" +
	"\t  D  dictionary match\n" +
	"\t  I  unseen word, strategies in order of application:\n" +
	"\t   a left and right match produce the same gloss(es)\n" +
	"\t   b right starts with left or left ends with right;*\n" +
	"\t   c right contains left => right minus everthing after left OR\n" +
	"\t     left contains right => left minus everything before right*\n" +
	"\t   d left ends with the begin of right => concatenate;*\n" +
	"\t   e right starts with left or left ends with with right;\n" +
	"\t   f dictionary gloss that starts with left and ends with right;\n" +
	"\t   g dictionary gloss that starts with the beginning of left and ends with the end of right;**\n" +
	"\t   h left or right found as dictionary gloss;***\n" +
	"\t   i (begin of) left or (end of) right found as dictionary gloss;\n" +
	"\t   j dictionary glosses beginning with left or ending with right;\n" +
	"\t      notes: *   >1 characters match,\n" +
	"\t             **  >2 characters match, \n" +
	"\t             *** dictionary frequency >1 to prevent overspecific outliers\n" +
	"\t      CODE can be used for debugging, also for assessing prediction quality\n" +
	"\tLEFT  most probable left-to-right dictionary annotation(s) for unseen words\n" +
	"\tRIGHT most probable right-to-left dictionary annotations (= longest form match)\n" +
	"\tPREC  predicted annotation retrieved from DICT, and RIGHT (cf. CODE)\n" +
	"\t      selection criteria: most frequent glosses/form > most frequent glosses > shortest glosses",
	formatter_class=argparse.RawDescriptionHelpFormatter)
args.add_argument("dicts", type=str, nargs="+", help="DICTi TSV dictionary: FORM<TAB>GLOSS[<TAB>FREQ[<TAB>...]]")

	
args=args.parse_args()

# todo: baseline: use annotation of the *first*, the overall frequency
# todo: eval mode: exclude full matches from left and right matches
# todo: parameterized for methods, and their order

form2gloss2freq={}
form2firstgloss={} # to get a baseline score

for a in args.dicts:
	if os.path.exists(a):
		sys.stderr.write("processing " + a+"\n")
		sys.stderr.flush()

		with open(a, "r") as input:
			for line in input:
				line=line.rstrip()
				fields = line.split("\t")
				if len(fields) > 1:
					form = fields[0]
					gloss = fields[1]
					freq = 1
					if len(fields) > 2:
						try:
							freq = std.stoi(fields[2])
						except Exception as e:
							sys.stderr.write("warning: "+str(e)+"\n"+
								"while reading \"" + fields[2] + "\"\n")
							sys.stderr.flush()

					if not form in form2gloss2freq:
						form2gloss2freq[form]={}
						form2firstgloss[form]=gloss

					if not gloss in form2gloss2freq[form]:
						form2gloss2freq[form][gloss]=0

					form2gloss2freq[form][gloss]+=freq


sys.stderr.write("registered " + str(len(form2gloss2freq)) + " forms\n")
sys.stderr.flush()

sys.stderr.write("optimize index\n")
sys.stderr.flush()

## fix indixes

# form-gloss index for partial matches
left2gloss2freq={}
for form in form2gloss2freq:
	for i in range(1,len(form)+1):
		for gloss in form2gloss2freq[form]:
			for j in range(1,len(gloss)+1):
				left = form[0:i+1]
				lgl = gloss[0:j+1]
				if not left in left2gloss2freq:
					left2gloss2freq[left]={}
				if not lgl in left2gloss2freq[left]:
					left2gloss2freq[left][lgl]=0
				left2gloss2freq[left][lgl]+=1

right2gloss2freq={}
for form in form2gloss2freq:
	for i in range(1,len(form)+1):
		for gloss in form2gloss2freq[form]:
			for j in range(1,len(gloss)+1):
				right = form[len(form) - i: ]
				lgl = gloss[len(gloss) - j: ]
				if not right in right2gloss2freq:
					right2gloss2freq[right]={}
				if not lgl in right2gloss2freq[right]:
					right2gloss2freq[right][lgl]=0
				right2gloss2freq[right][lgl]+=1

# gloss index for "reconstructing" independently from forms
gloss2freq={}
for f in form2gloss2freq:
	for g in form2gloss2freq[f]:
		if not g in gloss2freq:
			gloss2freq[g]=0
		gloss2freq[g]+=form2gloss2freq[f][g]

lg2gloss={}
rg2gloss={}
for g in gloss2freq:
	for i in range(0,len(g) - 2):
		lg = g[0: i+1]
		rg = g[len(g) - i: ]
		if not lg in lg2gloss: lg2gloss[lg]=set([])
		if not rg in rg2gloss: rg2gloss[rg]=set([])
		lg2gloss[lg].add(g)
		rg2gloss[rg].add(g)

sys.stderr.write("annotate first TAB-separated column from stdin\n")
sys.stderr.flush()

for line in sys.stdin:
	line=line.rstrip()
	print(line,end="")
	if not line.startswith("#") and line.strip()!="":
		form = re.sub(r"[#\s].*","",line).strip()
		# print("<F>"+form+"</F>")

		#originally, these are treesets, these do not exist in python
		glossPrev = set([])	
		glossLeft = set([])
		glossRight = set([])
		glossMrg = set([])
		if form != "":

			# baseline prediction: *first* gloss in dictionary
			if form in form2firstgloss:
				print("\t" + form2firstgloss[form], end="")
			else:
				print("\t_",end="")

			# dictionary-based prediction: most frequent previous annotations
			if form in form2gloss2freq:
				freq = 0
				for g in form2gloss2freq[form]:
					if form2gloss2freq[form][g] > freq:
						freq = form2gloss2freq[form][g]
						glossPrev.clear()

					if form2gloss2freq[form][g] == freq:
						glossPrev.add(g)




			# annotation of *UNSEEN* forms by extrapolation from left and right match and the dictionary

			# find maximum left match
			if len(glossPrev) > 0:
				print("\tD",end="") # dictionary-based
				glossLeft.add("_") # (no left and right matches necessary)
				glossRight.add("_")
				glossMrg = deepcopy(glossPrev)
			else:
				print("\tI",end="")		# inferred
				left = form
				while(len(left)>0 and not left in left2gloss2freq):
					left=left[0:len(left)-1]
				if(left in left2gloss2freq):
					
					# find most frequent glosses
					glosses = set([]) # orig treeset
					freq = 0
					for gloss in left2gloss2freq[left]:  
						if(left2gloss2freq[left][gloss] > freq):
							glosses.clear()
							freq = left2gloss2freq[left][gloss]
						 
						if(left2gloss2freq[left][gloss]==freq):
							glosses.add(gloss)
					 
					
					# eliminate all glosses contained in a longer one from the result set
					for gloss in set(glosses):
						for gl2 in set(glosses):
							if(gl2 != gloss and gl2.startswith(gloss)):
								if(gloss in glosses):
									glosses.remove(gloss)
						 

					# return all glosses
					glossLeft=glosses
				 
			
				# find maximum right match
				right = form.strip()
				while(len(right)>0 and not right in right2gloss2freq):
					#print("<R>"+right+"</R>", list(right2gloss2freq.keys())[0:10])
					right=right[1:]
				if(right in right2gloss2freq): 
					#print("<R>"+right+"</R>",right2gloss2freq[right])
					
					# find most frequent glosses
					glosses = set([])
					freq = 0
					for gloss in right2gloss2freq[right]:  
						if(right2gloss2freq[right][gloss] > freq):
							glosses.clear()
							freq = right2gloss2freq[right][gloss]
						 
						if(right2gloss2freq[right][gloss]==freq):
							glosses.add(gloss)
						 
					 
					
					# eliminate all glosses contained in a longer one from the result set
					for gloss in set(glosses):
						for gl2 in set(glosses):
							if(gl2 != gloss and gl2.endswith(gloss)):
								if(gloss in glosses):
									glosses.remove(gloss)
						 

					# return all glosses in lexicographic order
					glossRight= deepcopy(glosses)
				 
		
				# merge glossLeft and glossRight, using different merging strategies
				# originally a TreeSet
				glossMrg = deepcopy(glossPrev)
				
				# (a) both contain the same analysis
				if(len(glossMrg)==0):  
					print("a",end="")
					for l in glossLeft:
						if(len(l.strip())>0):
							for r in glossRight:
								if(len(r.strip())>0):
									if(l==r):
										glossMrg.add(l)
				 
				
				# (b) right starts with left or left ends with with right
				# we require at least two characters to match
				if(len(glossMrg)==0):  
					print("b",end="")
					for l in glossLeft:
						if(len(l.strip())>1):
							for r in glossRight:
								if(len(r.strip())>1):
									if(l.endswith(r)):  
										glossMrg.add(l)
									elif(r.startswith(l)):
										glossMrg.add(r)
				 

				# (c) right contains left => right minus everthing after left
				#     left contains right => left minus everything before right
				# we require at least two characters to match
				if(len(glossMrg)==0):  
					print("c",end="")
					for l in glossLeft:
						if(len(l.strip())>1):
							for r in glossRight:
								if(len(r.strip())>1):
									if(l in r):  
										glossMrg.add(r.split(l)[0]+l) # r.replaceFirst(l+".*",l)
									elif(r in l): 
										glossMrg.add(l.split(r)[0]+r) # l.replaceFirst(r+".*",r))
				 

				# (d) left ends with the begin of right => concatenate
				#     left contains right => left minus everything before right
				# we require at least two characters to match
				# return the sequences with maximum overlap, only
				if(len(glossMrg)==0):
					print("d",end="")
					overlap = 2
					for  l in glossLeft:
						if(len(l.strip())>overlap):
							for r in glossRight:
								if(len(r.strip())>overlap):
									if(l.endswith(r[0:overlap])):
										glossMrg.add(l+r[overlap:]) # l+r.substring(overlap))
									 
									while(l.endswith(r[0:overlap+1])):  
										glossMrg.clear()
										overlap+=1
										glossMrg.add(l+r[overlap:])
				
				# (e) right starts with left (=> right) or left ends with with right (=> left)
				# no length restrictions
				if(len(glossMrg)==0):
					print("e",end="")
					for l in glossLeft:
						if(len(l.strip())>0):
							for  r in glossRight:
								if(len(r.strip())>0):
									if(l.endswith(r)):
										glossMrg.add(l)
									elif(r.startswith(l)):
										glossMrg.add(r)
				 
				
				
				# "reconstruction" using gloss index, cf.
				# Hashtable<String,Integer> gloss2freq = new Hashtable<String,Integer>()
				# Hashtable<String,Set<String>> lg2gloss = new Hashtable<String,Set<String>>()
				# Hashtable<String,Set<String>> rg2gloss = new Hashtable<String,Set<String>>()
		
								
				# (f) gloss(es) starting with left and ends with right
				# frequency disambiguation below
				if(len(glossMrg)==0):
					print("f",end="")
					for  lg in glossLeft:
						if(lg in lg2gloss):
							for  g in lg2gloss[lg]:
								for rg in glossRight:
									if(rg in rg2gloss):
										if(g in rg2gloss[rg]):
											glossMrg.add(g)
				 
				
				# (g) gloss that starts with the beginning of left (>2) and ends with the ending of right
				# pick the one with maximum overlap
				# frequency disambiguation below
				if(len(glossMrg)==0): 
					print("g",end="")
					overlap = 0
					for lg in glossLeft:
						for i in range(2, len(lg)):
							for rg in glossRight:
								for j in range( 2, len(rg)):
									if(j+i>=overlap):
										l = lg[0:i]
										r = rg[len(rg)-j-1:]
										if(l in lg2gloss and r in rg2gloss):
											for  g in lg2gloss[l]:
												if(g in rg2gloss[r]):  
													if(i+j>overlap):
														overlap=i+j
														glossMrg.clear()
													 
													if(overlap==i+j):
														glossMrg.add(g)
				 

				# left *or* right match
				# (h) all complete left or right glosses with freq > 1 (to prohibit overspecific outliers)
				if(len(glossMrg)==0):  
					print("h",end="")
					for lg in glossLeft:
						if(len(lg)>1):
							if(lg in gloss2freq and gloss2freq[lg]>1):
								glossMrg.add(lg)
					for rg in glossRight:
						if(len(rg)>1):
							if(rg in gloss2freq and gloss2freq[rg]>1):
								glossMrg.add(rg)
				
				# (i) most frequent left and right fragment
				# frequency disambiguation below, thus, just all substrings )
				if(len(glossMrg)==0):  
					print("i",end="")
					for lg in glossLeft :
						l = lg
						while(len(l)>1):
							if(l in gloss2freq):
								glossMrg.add(l)						
							l=l[0:len(l)-1]
						
						
						
					for rg in glossRight:
						r = rg
						while(len(r)>1):
							if(r in gloss2freq):
								glossMrg.add(r)					
							r=r[1:]
				 

				# (j) expand most frequent gloss
				# frequency disambiguation below, thus, just all superstrings )
				if(len(glossMrg)==0):
					print("j",end="")
					for lg in glossLeft:
						if(len(lg)>0 and lg in lg2gloss):
							for  g in lg2gloss.get(lg):
								glossMrg.add(g)
					for  rg in glossRight:
						if(len(rg)>0 and rg in rg2gloss):
							for  g in rg2gloss[rg]:
								glossMrg.add(g)
				 
			 
		
			# disambiguate glossMrg with frequency
			if(len(glossMrg)>1):
				# sys.stderr.write(glossMrg)
				freq = -1
				tmp = glossMrg
				glossMrg  = set([])
				for  g in tmp:
					if(len(g.strip())>0):  
						if(freq==-1):   
							if(g in gloss2freq):
								glossMrg.clear()
								freq=gloss2freq[g]
							 
							glossMrg.add(g)
						else:
							if(g in gloss2freq and gloss2freq[g]>freq):
								glossMrg.clear()
								freq=gloss2freq[g]
							 
							if(g in gloss2freq and gloss2freq[g]==freq):
								glossMrg.add(g)
								# sys.stderr.write(glossMrg+" "+freq+"\n")
							 
						 
					 
				# sys.stderr.write("=> "+glossMrg+ " (freq: "+freq+")+\n")

				# disambiguate glossMrg with brevity
				if(len(glossMrg)>1):  
					length = sys.maxsize
					tmp=glossMrg
					glossMrg  = set([])
					for g in tmp:
						if(len(g.strip())>0):  
							if(len(g)<length):
								length=len(g)
								glossMrg.clear()
							  
							if(len(g)==length):
								glossMrg.add(g)
								# sys.stderr.write(glossMrg+" "+length)
							 
					# sys.stderr.write("=> "+glossMrg+ " (length disamb)")
				 
				# sys.stderr.write()
		
		# originally, these were TreeSets, so they get ordered lists now
		glossPrev = sorted(glossPrev)
		glossLeft = sorted(glossLeft)
		glossRight = sorted(glossRight)
		glossMrg = sorted(glossMrg)			 
		
		if(len(glossPrev)==0): 
			glossPrev.append("_")
		print("\t"+glossPrev.pop(),end="")
		while(len(glossPrev)>1):
			print("|"+glossPrev.pop(),end="")
		 
		if(len(glossLeft)==0):
			glossLeft.append("_")
		print("\t"+glossLeft.pop(),end="")
		while(len(glossLeft)>1):  
			print("|"+glossLeft.pop(),end="")
		 
		if(len(glossRight)==0):
			glossRight.append("_")
		print("\t"+glossRight.pop(),end="")
		while(len(glossRight)>1):
			print("|"+glossRight.pop(),end="")
		 
		if(len(glossMrg)==0):
			glossMrg.append("_")
		print("\t"+glossMrg.pop(),end="")
		while(len(glossMrg)>1):  
			print("|"+glossMrg.pop(),end="")
		 
	 
	print()
 
 