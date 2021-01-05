import java.io.*;
import java.util.*;

public class Glosser {

	public static void main(String argv[]) throws Exception {
		System.err.println("synopsis: Glosser [-h] DICT1[..n]\n"+
		"DEPRECATION NOTE: This program has been ported to Python and is no longer maintained\n"+
		"Please use glosser.py, instead");
		if(argv[0].toLowerCase().startsWith("-h"))
			System.err.println(
				"\tDICTi TSV dictionary: FORM<TAB>GLOSS[<TAB>FREQ[<TAB>...]]\n"+
				"reads one word per line from stdin, at most until the first TAB character\n"+
				"writes TAB-separated values to stdout:\n"+
				"\tORIG original TSV columns\n"+
				"\tBASE baseline: returns the first annotation found in DICTs\n"+
				"\tDICT most frequent gloss(es), based on dictionary lookup\n"+
				"\tCODE strategies to predict gloss annotation:\n"+
				"\t  D  dictionary match\n"+
				"\t  I  unseen word, inference strategies in order of application:\n"+
				"\t   a left and right match produce the same gloss(es)\n"+
				"\t   b right starts with left or left ends with right;*\n"+
				"\t   c right contains left => right minus everthing after left OR\n"+
				"\t     left contains right => left minus everything before right*\n"+
				"\t   d left ends with the begin of right => concatenate;*\n"+
				"\t   e right starts with left or left ends with with right;\n"+
				"\t   f dictionary gloss that starts with left and ends with right;\n"+
				"\t   g dictionary gloss that starts with the beginning of left and ends with the end of right;**\n"+
				"\t   h left or right found as dictionary gloss;***\n"+
				"\t   i (begin of) left or (end of) right found as dictionary gloss;\n"+
				"\t   j dictionary glosses beginning with left or ending with right;\n"+
				"\t      notes: *   >1 characters match,\n"+
				"\t             **  >2 characters match, \n"+
				"\t             *** dictionary frequency >1 to prevent overspecific outliers\n"+
				"\t      CODE can be used for debugging, but also for assessing prediction quality\n"+
				"\tLEFT  most probable left-to-right dictionary annotation(s) for unseen words\n"+
				"\tRIGHT most probable right-to-left dictionary annotations (= longest form match)\n"+
				"\tPREC  predicted annotation retrieved from DICT, LEFT and RIGHT (cf. CODE)\n"+
				"\t      selection criteria: most frequent glosses/form > most frequent glosses > shortest glosses");
		
		// todo: baseline: use annotation of the *first*, not the overall frequency
		// todo: eval mode: exclude full matches from left and right matches
		// todo: parameterized for methods, thresholds and their order
		
		Hashtable<String,Hashtable<String,Integer>> form2gloss2freq = new Hashtable<String, Hashtable<String,Integer>>();
		Hashtable<String,String> form2firstgloss = new Hashtable<String,String>(); // to get a baseline score
		
		for(String a : argv) {
			if((new File(a)).exists()) {
				System.err.println("processing "+a);
				BufferedReader in = new BufferedReader(new FileReader(a));
				for(String line = in.readLine(); line!=null; line=in.readLine()) {
					String[] fields = line.split("\t");
					if(fields.length>1) {
						String form = fields[0];
						String gloss = fields[1];
						int freq = 1;
						if(fields.length>2) {
							try {
								freq = Integer.parseInt(fields[2]);
							} catch(NumberFormatException e) {
								e.printStackTrace();
								System.err.println("while reading \""+fields[2]+"\"");
							}
						}
						if(form2gloss2freq.get(form)==null) {
							form2gloss2freq.put(form,new Hashtable<String,Integer>());
							form2firstgloss.put(form,gloss);
						}
						if(form2gloss2freq.get(form).get(gloss)==null)
							form2gloss2freq.get(form).put(gloss,0);
						form2gloss2freq.get(form).put(gloss,form2gloss2freq.get(form).get(gloss)+freq);
					}
				}
			}
		}
		
		System.err.println("registered "+form2gloss2freq.size()+" forms");
		
		System.err.println("optimize index");
		// form-gloss index for partial matches
		Hashtable<String,Hashtable<String,Integer>> left2gloss2freq = new Hashtable<String,Hashtable<String,Integer>>();
		for(String form : form2gloss2freq.keySet())
			for(int i=1; i<=form.length(); i++)
				for(String gloss : form2gloss2freq.get(form).keySet())
					for(int j=1;j<=gloss.length(); j++) {
						String left = form.substring(0,i);
						String lgl = gloss.substring(0,j);
						if(left2gloss2freq.get(left)==null) 
							left2gloss2freq.put(left, new Hashtable<String,Integer>());
						if(left2gloss2freq.get(left).get(lgl)==null)
							left2gloss2freq.get(left).put(lgl,0);
						left2gloss2freq.get(left).put(lgl,left2gloss2freq.get(left).get(lgl)+1);
					}
		Hashtable<String,Hashtable<String,Integer>> right2gloss2freq = new Hashtable<String,Hashtable<String,Integer>>();
		for(String form : form2gloss2freq.keySet())
			for(int i=1; i<=form.length(); i++)
				for(String gloss : form2gloss2freq.get(form).keySet())
					for(int j=1;j<=gloss.length(); j++) {
						String right = form.substring(form.length()-i,form.length());
						String lgl = gloss.substring(gloss.length()-j,gloss.length());
						if(right2gloss2freq.get(right)==null) 
							right2gloss2freq.put(right, new Hashtable<String,Integer>());
						if(right2gloss2freq.get(right).get(lgl)==null)
							right2gloss2freq.get(right).put(lgl,0);
						right2gloss2freq.get(right).put(lgl,right2gloss2freq.get(right).get(lgl)+1);
					}

		// gloss index for "reconstructing" independently from forms
		Hashtable<String,Integer> gloss2freq = new Hashtable<String,Integer>();
		for(String f : form2gloss2freq.keySet())
			for(String g : form2gloss2freq.get(f).keySet()) {
				if(gloss2freq.get(g)==null)
					gloss2freq.put(g,0);
				gloss2freq.put(g,gloss2freq.get(g)+form2gloss2freq.get(f).get(g));;
			}
		Hashtable<String,Set<String>> lg2gloss = new Hashtable<String,Set<String>>();
		Hashtable<String,Set<String>> rg2gloss = new Hashtable<String,Set<String>>();
		for(String g : gloss2freq.keySet()) 
			for(int i = 0; i<g.length()-2; i++) {
				String lg = g.substring(0,i);
				String rg = g.substring(g.length()-i, g.length());
				if(lg2gloss.get(lg)==null) lg2gloss.put(lg,new HashSet<String>());
				if(rg2gloss.get(rg)==null) rg2gloss.put(rg,new HashSet<String>());
				lg2gloss.get(lg).add(g);
				rg2gloss.get(rg).add(g);
			}
			
		

		System.err.println("annotate first TAB-separated column from stdin");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		for(String line = in.readLine(); line!=null; line=in.readLine()){
			System.out.print(line);
			if(!line.startsWith("#") && !line.trim().equals("")) {
				String form=line.replaceFirst("[#\t].*","").trim();
						
				NavigableSet<String> glossPrev = new TreeSet<String>();
				NavigableSet<String> glossLeft = new TreeSet<String>();
				NavigableSet<String> glossRight = new TreeSet<String>();
				NavigableSet<String> glossMrg = new TreeSet<String>();
				if(!form.equals("")) {
					
					// baseline prediction: *first* gloss in dictionary
					if(form2firstgloss.get(form)!=null) {
						System.out.print("\t"+form2firstgloss.get(form));
					} else 
						System.out.print("\t_");
					
					// dictionary-based prediction: most frequent previous annotations
					if(form2gloss2freq.get(form)!=null) {
						int freq = 0;
						for(String g : form2gloss2freq.get(form).keySet()) {
							if(form2gloss2freq.get(form).get(g) > freq) {
								freq = form2gloss2freq.get(form).get(g);
								glossPrev.clear();
							}
							if(form2gloss2freq.get(form).get(g) == freq) {
								glossPrev.add(g);
							}
						}
					}
					
					// annotation *UNSEEN* forms by extrapolation from left and right match and the dictionary

					// find maximum left match
					if(glossPrev.size()>0) {
						System.out.print("\tD");		// dictionary-based
						glossLeft.add("_"); // (no left and right matches necessary)
						glossRight.add("_");
						glossMrg = new TreeSet<String>(glossPrev);
					} else {
						System.out.print("\tI");		// inferred
						String left = form;
						while(left.length()>0 && left2gloss2freq.get(left)==null)
							left=left.substring(0,left.length()-1);
						if(left2gloss2freq.get(left)!=null) {
							
							// find most frequent glosses
							TreeSet<String> glosses = new TreeSet<String>();
							int freq = 0;
							for(String gloss : left2gloss2freq.get(left).keySet()) {
								if(left2gloss2freq.get(left).get(gloss) > freq) {
									glosses.clear();
									freq = left2gloss2freq.get(left).get(gloss);
								}
								if(left2gloss2freq.get(left).get(gloss)==freq) {
									glosses.add(gloss);
								}
							}
							
							// eliminate all glosses contained in a longer one from the result set
							for(String gloss : new HashSet<String>(glosses))
								for(String gl2 : new HashSet<String>(glosses)) {
									if(!gl2.equals(gloss) && gl2.startsWith(gloss))
										glosses.remove(gloss);
								}

							// return all glosses
							glossLeft=glosses;
						}
					
						// find maximum right match
						String right = form;
						while(right.length()>0 && right2gloss2freq.get(right)==null)
							right=right.substring(1);
						if(right2gloss2freq.get(right)!=null) {
							
							// find most frequent glosses
							TreeSet<String> glosses = new TreeSet<String>();
							int freq = 0;
							for(String gloss : right2gloss2freq.get(right).keySet()) {
								if(right2gloss2freq.get(right).get(gloss) > freq) {
									glosses.clear();
									freq = right2gloss2freq.get(right).get(gloss);
								}
								if(right2gloss2freq.get(right).get(gloss)==freq) {
									glosses.add(gloss);
								}
							}
							
							// eliminate all glosses contained in a longer one from the result set
							for(String gloss : new HashSet<String>(glosses))
								for(String gl2 : new HashSet<String>(glosses)) {
									if(!gl2.equals(gloss) && gl2.endsWith(gloss))
										glosses.remove(gloss);
								}

							// return all glosses in lexicographic order
							glossRight= glosses;
						}
				
						// merge glossLeft and glossRight, using different merging strategies
						
						glossMrg = new TreeSet<String>(glossPrev);
						
						// (a) both contain the same analysis
						if(glossMrg.size()==0) {
							System.out.print("a");
							for(String l : glossLeft)
								if(l.trim().length()>0)
									for(String r: glossRight)
										if(r.trim().length()>0)
											if(l.equals(r))
												glossMrg.add(l);
						}
						
						// (b) right starts with left or left ends with with right
						// we require at least two characters to match
						if(glossMrg.size()==0) {
							System.out.print("b");
							for(String l : glossLeft)
								if(l.trim().length()>1)
									for(String r: glossRight)
										if(r.trim().length()>1)
											if(l.endsWith(r)) {
												glossMrg.add(l);
											} else if(r.startsWith(l)) 
												glossMrg.add(r);
						}

						// (c) right contains left => right minus everthing after left
						//     left contains right => left minus everything before right
						// we require at least two characters to match
						if(glossMrg.size()==0) {
							System.out.print("c");
							for(String l : glossLeft)
								if(l.trim().length()>1)
									for(String r: glossRight)
										if(r.trim().length()>1)
											if(r.contains(l)) {
												glossMrg.add(r.replaceFirst(l+".*",l));
											} else if(l.contains(r)) 
												glossMrg.add(l.replaceFirst(r+".*",r));
						}

						// (d) left ends with the begin of right => concatenate
						//     left contains right => left minus everything before right
						// we require at least two characters to match
						// return the sequences with maximum overlap, only
						if(glossMrg.size()==0) {
							System.out.print("d");
							int overlap = 2;
							for(String l : glossLeft)
								if(l.trim().length()>overlap)
									for(String r: glossRight)
										if(r.trim().length()>overlap) {
											if(l.endsWith(r.substring(0,overlap))) {
												glossMrg.add(l+r.substring(overlap));
											}
											while(l.endsWith(r.substring(0,overlap+1))) {
												glossMrg.clear();
												glossMrg.add(l+r.substring(++overlap));
											}
										}
						}
						
						// (e) right starts with left (=> right) or left ends with with right (=> left)
						// no length restrictions
						if(glossMrg.size()==0) {
							System.out.print("e");
							for(String l : glossLeft)
								if(l.trim().length()>0)
									for(String r: glossRight)
										if(r.trim().length()>0)
											if(l.endsWith(r)) {
												glossMrg.add(l);
											} else if(r.startsWith(l)) 
												glossMrg.add(r);
						}
						
						
						// "reconstruction" using gloss index, cf.
						// Hashtable<String,Integer> gloss2freq = new Hashtable<String,Integer>();
						// Hashtable<String,Set<String>> lg2gloss = new Hashtable<String,Set<String>>();
						// Hashtable<String,Set<String>> rg2gloss = new Hashtable<String,Set<String>>();
				
										
						// (f) gloss(es) starting with left and ends with right
						// frequency disambiguation below
						if(glossMrg.size()==0) {
							System.out.print("f");
							for(String lg : glossLeft)
								if(lg2gloss.get(lg)!=null)
									for(String g : lg2gloss.get(lg))
										for(String rg :glossRight)
											if(rg2gloss.get(rg)!=null)
												if(rg2gloss.get(rg).contains(g))
													glossMrg.add(g);
						}
						
						// (g) gloss that starts with the beginning of left (>2) and ends with the ending of right
						// pick the one with maximum overlap
						// frequency disambiguation below
						if(glossMrg.size()==0) {
							System.out.print("g");
							int overlap = 0;
							for(String lg : glossLeft)
								for(int i = 2; i <lg.length(); i++)
									for(String rg : glossRight)
										for(int j = 2; j<rg.length(); j++)
											if(j+i>=overlap) {
												String l = lg.substring(0,i);
												String r = rg.substring(rg.length()-j-1,rg.length());
												if(lg2gloss.get(l) != null && rg2gloss.get(r)!=null)
													for(String g : lg2gloss.get(l))
														if(rg2gloss.get(r).contains(g)) {
															if(i+j>overlap) {
																overlap=i+j;
																glossMrg.clear();
															}
															if(overlap==i+j)
																glossMrg.add(g);
														}
											}
						}

						// left *or* right match
						// (h) all complete left or right glosses with freq > 1 (to prohibit overspecific outliers)
						if(glossMrg.size()==0) {
							System.out.print("h");
							for(String lg : glossLeft)
								if(lg.length()>1)
									if(gloss2freq.get(lg)!=null && gloss2freq.get(lg)>1) glossMrg.add(lg);
							for(String rg : glossRight)
								if(rg.length()>1)
									if(gloss2freq.get(rg)!=null && gloss2freq.get(rg)>1) glossMrg.add(rg);
						}
						
						// (i) most frequent left and right fragment
						// frequency disambiguation below, thus, just all substrings ;)
						if(glossMrg.size()==0) {
							System.out.print("i");
							for(String lg : glossLeft) 
								for(String l = lg;
									l.length()>1;
									l=l.substring(l.length()-1))
									if(gloss2freq.get(l)!=null)
										glossMrg.add(l);
							for(String rg : glossRight)
								for(String r = rg;
									r.length()>1;
									r=r.substring(1))
									if(gloss2freq.get(r)!=null)
										glossMrg.add(r);
						}

						// (j) expand most frequent gloss
						// frequency disambiguation below, thus, just all superstrings ;)
						if(glossMrg.size()==0) {
							System.out.print("j");
							for(String lg : glossLeft)
								if(lg.length()>0 && lg2gloss.get(lg)!=null)
									for(String g : lg2gloss.get(lg))
										glossMrg.add(g);
							for(String rg : glossRight)
								if(rg.length()>0 && rg2gloss.get(rg)!=null)
									for(String g : rg2gloss.get(rg))
										glossMrg.add(g);
						}
					}
				
					// disambiguate glossMrg with frequency
					if(glossMrg.size()>1) {
						// System.err.println(glossMrg);
						int freq = -1;
						Set<String> tmp = glossMrg;
						glossMrg  = new TreeSet<String>();
						for(String g : tmp)
							if(g.trim().length()>0) {
								if(freq==-1) { 
									if(gloss2freq.get(g)!=null) {
										glossMrg.clear();
										freq=gloss2freq.get(g);
									}
									glossMrg.add(g);
								} else {
									if(gloss2freq.get(g)!=null && gloss2freq.get(g)>freq) {
										glossMrg.clear();
										freq=gloss2freq.get(g);
									}
									if(gloss2freq.get(g)!=null && gloss2freq.get(g)==freq) {
										glossMrg.add(g);
										// System.err.println(glossMrg+" "+freq);
									}
								}
							}
						// System.err.println("=> "+glossMrg+ " (freq: "+freq+")");

						// disambiguate glossMrg with brevity
						if(glossMrg.size()>1) {
							int length = Integer.MAX_VALUE;
							tmp=glossMrg;
							glossMrg  = new TreeSet<String>();
							for(String g : tmp)
								if(g.trim().length()>0) {
									if(g.length()<length) {
										length=g.length();
										glossMrg.clear();
									} 
									if(g.length()==length) {
										glossMrg.add(g);
										// System.err.println(glossMrg+" "+length);
									}
								}
							// System.err.println("=> "+glossMrg+ " (length disamb)");
						}
						// System.err.println();
					}
				}
					
				if(glossPrev.size()==0) glossPrev.add("_");
				System.out.print("\t"+glossPrev.first());
				while(glossPrev.size()>1) {
					glossPrev = glossPrev.tailSet(glossPrev.first(),false);
					System.out.print("|"+glossPrev.first());
				}
				
				if(glossLeft.size()==0) glossLeft.add("_");
				System.out.print("\t"+glossLeft.first());
				while(glossLeft.size()>1) {
					glossLeft = glossLeft.tailSet(glossLeft.first(),false);
					System.out.print("|"+glossLeft.first());
				}
				
				if(glossRight.size()==0) glossRight.add("_");
				System.out.print("\t"+glossRight.first());
				while(glossRight.size()>1) {
					glossRight = glossRight.tailSet(glossRight.first(),false);
					System.out.print("|"+glossRight.first());
				}
				
				if(glossMrg.size()==0) glossMrg.add("_");
				System.out.print("\t"+glossMrg.first());
				while(glossMrg.size()>1) {
					glossMrg=glossMrg.tailSet(glossMrg.first(),false);
					System.out.print("|"+glossMrg.first());
				}
			}
			System.out.println();
		}
	}
}