package testpopupmenu.handlers;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.commands.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.internal.corext.callhierarchy.*;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SampleHandler extends AbstractHandler {
	/**
	 * The constructor.
	 */
	public SampleHandler() {
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		//record the time
		long startTime = System.currentTimeMillis();
		// get workbench window
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		// set selection service
		ISelectionService service = window.getSelectionService();
		// set structured selection
		IStructuredSelection structured = (IStructuredSelection) service.getSelection();
		// store the output string
		String output = new String();
		// store the result
		boolean res = true;
		// store project name
		String projectName = new String();
		// store the number of the paths;
		int numOfPaths = 0;
		// store the number of the paths;
		int numOfRightPaths = 0;
		// store the number of the paths;
		int numOfWrongPaths = 0;
		//check if it is an IProject
		if (structured.getFirstElement() instanceof IProject) {
			// get the selected Project
			IProject project = (IProject) structured.getFirstElement();
			projectName = project.getName();
			System.out.println("Working in project " + project.getName());
			output += "Working in project " + project.getName() + "\n";
			//line of code
			IJavaProject javaProject = JavaCore.create(project);
			IPackageFragment[] packages;
			int loc = 0;
			try {
				packages = javaProject.getPackageFragments();
				for (IPackageFragment mypackage : packages) {
				      // Package fragments include all packages in the
				      // classpath
				      // We will only look at the package from the source
				      // folder
				      // K_BINARY would include also included JARS, e.g.
				      // rt.jar
				      if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
	
				        for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
				        	Document doc = new Document(unit.getSource());
				        	loc += doc.getNumberOfLines();
				            
		      	        }
				      }
				    }
			} catch (JavaModelException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			System.out.println("Has number of lines: " + loc);
			output += "Has number of lines: " + loc + "\n";
			
			List<List> allPaths = new ArrayList<List>();
			
			int[] levelFindLoop = new int[3];
			
			// get types including Thread and all the subclasses of the Thread
			List<IType> threadAndSubclassesOfThread = getSubClasses(project);
			System.out.println("This project has "+threadAndSubclassesOfThread.size()+" subclasses of thread (including Thread itself)");
			output += "This project has " + threadAndSubclassesOfThread.size()+" subclasses of thread (including Thread itself)" + "\n";
			for (IType type : threadAndSubclassesOfThread) {
				System.out.println(type.getElementName());
				output += type.getElementName() + "\n";
			}
			// These 6 variable are used to calculate the number of paths for each level
			int levelOneCallers = 0;
			int levelOneLeaves = 0;
			int levelTwoCallers = 0;
			int levelTwoLeaves = 0;
			int levelThreeCallers = 0;
			int levelThreeLeaves = 0;
			// These 4 variables are used to count how many bound path is concluded by which pattern.
			int numOfPatternOne = 0;
			int numOfPatternTwo = 0;
			int numOfPatternThree = 0;
			int numOfPatternFour = 0;
			
			
			for(IType type : threadAndSubclassesOfThread){
				System.out.println("Working in class " + type.getElementName());
				output += "Working in class " + type.getElementName() + "\n";
				try {
					IMethod[] methods = type.getMethods();
					for (IMethod method : methods){
						//!!!it is possible that this type does not have a constructor by itself, it can be initiated by its super class. Currently, we ignore these classes.
						if (method.isConstructor()){
							//level 1 methods that call the constructor of thread or the subclass of thread
							HashSet<IMethod> callers = getCallersOf(method);
							//no calls for the constructor
							if(callers.size() == 0){
								continue;
							}
							
							
							for (IMethod caller : callers){
								//thread & worker£¬when dealing with thread, our algorithm should not consider the caller from the constructors of thread's subclasses
								if (type.getElementName().equals("Thread") ) {
									//if this caller is the constructor, we can ignore it and continue to next caller, since it will be considered by a subclass of thread
									if(caller.isConstructor()){
										continue;
									}
								}
								MethodDeclaration callerDeclaration = getMethodDeclarationFromIMethod(project, caller);
								//some callers are in the jar files which we don't care.
								if(callerDeclaration == null){
									continue;
								}
								levelOneCallers++;
								System.out.println("level 1 method declaration:\n" + callerDeclaration);
								output += "level 1 method declaration:\n" + callerDeclaration + "\n";
								List<List> levelOnePath = findPath(callerDeclaration, method, 1);
								//sometimes we can't find callee in the caller method body, ex,: implicit super().
								if(levelOnePath.size() == 0){
									levelOnePath.add(new ArrayList());
								}
								
								//level 2 methods that call the caller in level 1
								HashSet<IMethod> callerers = getCallersOf(caller);
								//level 1 is leaf
								if(callerers.size() == 0){
								//	allPaths.addAll(levelOnePath);
									for(int i = 0; i < levelOnePath.size(); i++){
										levelOneLeaves++;
										numOfPaths++;
										int analysisRes = analyzePath(levelOnePath.get(i), levelFindLoop);
										if(analysisRes == 0){
											numOfWrongPaths++;
											System.out.println(levelOnePath.get(i));
											output += levelOnePath.get(i)+"\n";
											System.out.println("This path is wrong!");
											output += "This path is wrong!\n";
											res = false;
										}else{
											numOfRightPaths++;
											System.out.println("This path is right!");
											output += "This path is right!\n";
											switch (analysisRes) {
												case 1:
													System.out.println("satisfy pattern 1");
													output += "satisfy pattern 1\n";
													numOfPatternOne++;
													break;
												case 2:
													System.out.println("satisfy pattern 2");
													output += "satisfy pattern 1\n";
													numOfPatternTwo++;
													break;
												case 3:
													System.out.println("satisfy pattern 3");
													output += "satisfy pattern 1\n";
													numOfPatternThree++;
													break;
												case 4:
													System.out.println("satisfy pattern 4");
													output += "satisfy pattern 1\n";
													numOfPatternFour++;
													break;
											}
												
										}
									}
									continue;
								}
								int nullCallererCount = 0;
								
								for (IMethod callerer : callerers){
									MethodDeclaration callererDeclaration = getMethodDeclarationFromIMethod(project, callerer);
									//some callerers are in the jar files which we don't care.
									
									if(callererDeclaration == null){
										nullCallererCount++;
										if(nullCallererCount == callerers.size()){
											for(int i = 0; i < levelOnePath.size(); i++){
												levelOneLeaves++;
												numOfPaths++;
												int analysisRes = analyzePath(levelOnePath.get(i), levelFindLoop);
												if(analysisRes == 0){
													numOfWrongPaths++;
													System.out.println(levelOnePath.get(i));
													output += levelOnePath.get(i)+"\n";
													System.out.println("This path is wrong!");
													output += "This path is wrong!\n";
													res = false;
												}else{
													numOfRightPaths++;
													System.out.println("This path is right!");
													output += "This path is right!\n";
													switch (analysisRes) {
													case 1:
														System.out.println("satisfy pattern 1");
														output += "satisfy pattern 1\n";
														numOfPatternOne++;
														break;
													case 2:
														System.out.println("satisfy pattern 2");
														output += "satisfy pattern 1\n";
														numOfPatternTwo++;
														break;
													case 3:
														System.out.println("satisfy pattern 3");
														output += "satisfy pattern 1\n";
														numOfPatternThree++;
														break;
													case 4:
														System.out.println("satisfy pattern 4");
														output += "satisfy pattern 1\n";
														numOfPatternFour++;
														break;
													}
												}
											}
										}
										continue;
									}
									levelTwoCallers++;
									System.out.println("level 2 method declaration:\n" + callererDeclaration);
									output += "level 2 method declaration:\n" + callererDeclaration + "\n";
									List<List> levelTwoPath = findPath(callererDeclaration, caller, 2);
									//sometimes we can't find callee in the caller method body, ex,: implicit super().
									if(levelTwoPath.size() == 0){
										levelTwoPath = levelOnePath;
									}else {
										List<List> tempLevelTwo = new ArrayList<List>();
										for(int i = 0; i < levelTwoPath.size(); i++){
											for(int j = 0; j < levelOnePath.size(); j++){
												ArrayList path = new ArrayList();
												path.addAll(levelTwoPath.get(i));
												path.addAll(levelOnePath.get(j));
												tempLevelTwo.add(path);
											}
										}
										levelTwoPath = tempLevelTwo;
									}
									
									//level 3 methods that call the callerer in level 2
									HashSet<IMethod> callererers = getCallersOf(callerer);
									//level 2 is leaf
									if(callererers.size() == 0){
									//	allPaths.addAll(levelTwoPath);
										for(int i = 0; i < levelTwoPath.size(); i++){
											numOfPaths++;
											levelTwoLeaves++;
											int analysisRes = analyzePath(levelTwoPath.get(i), levelFindLoop);
											if(analysisRes == 0){
												numOfWrongPaths++;
												System.out.println(levelTwoPath.get(i));
												output += levelTwoPath.get(i)+"\n";
												System.out.println("This path is wrong!");
												output += "This path is wrong!\n";
												res = false;
											}else{
												numOfRightPaths++;
												System.out.println("This path is right!");
												output += "This path is right!\n";
												switch (analysisRes) {
												case 1:
													System.out.println("satisfy pattern 1");
													output += "satisfy pattern 1\n";
													numOfPatternOne++;
													break;
												case 2:
													System.out.println("satisfy pattern 2");
													output += "satisfy pattern 1\n";
													numOfPatternTwo++;
													break;
												case 3:
													System.out.println("satisfy pattern 3");
													output += "satisfy pattern 1\n";
													numOfPatternThree++;
													break;
												case 4:
													System.out.println("satisfy pattern 4");
													output += "satisfy pattern 1\n";
													numOfPatternFour++;
													break;
												}
											}
										}
										continue;
									}
									int nullCallerererCount = 0;
									
									for (IMethod callererer : callererers) {
										MethodDeclaration callerererDeclaration = getMethodDeclarationFromIMethod(project, callererer);
										//some callererers are in the jar files which we don't care.
										
										if(callerererDeclaration == null){
											nullCallerererCount++;
											if(nullCallerererCount == callererers.size()){
												for(int i = 0; i < levelTwoPath.size(); i++){
													numOfPaths++;
													int analysisRes = analyzePath(levelTwoPath.get(i), levelFindLoop);
													if(analysisRes==0){
														levelTwoLeaves++;
														numOfWrongPaths++;
														System.out.println(levelTwoPath.get(i));
														output += levelTwoPath.get(i)+"\n";
														System.out.println("This path is wrong!");
														output += "This path is wrong!\n";
														res = false;
													}else{
														numOfRightPaths++;
														System.out.println("This path is right!");
														output += "This path is right!\n";
														switch (analysisRes) {
														case 1:
															System.out.println("satisfy pattern 1");
															output += "satisfy pattern 1\n";
															numOfPatternOne++;
															break;
														case 2:
															System.out.println("satisfy pattern 2");
															output += "satisfy pattern 1\n";
															numOfPatternTwo++;
															break;
														case 3:
															System.out.println("satisfy pattern 3");
															output += "satisfy pattern 1\n";
															numOfPatternThree++;
															break;
														case 4:
															System.out.println("satisfy pattern 4");
															output += "satisfy pattern 1\n";
															numOfPatternFour++;
															break;
														}
													}
												}
											}
											continue;
										}
										levelThreeCallers++;
										System.out.println("level 3 method declaration:\n" + callerererDeclaration);
										output += "level 3 method declaration:\n" + callerererDeclaration + "\n";
										List<List> levelThreePath = findPath(callerererDeclaration, callerer, 3);
										if(levelThreePath.size() == 0){
											levelThreePath = levelTwoPath;
										}else {
											List<List> tempLevelThree = new ArrayList<List>();
											for(int i = 0; i < levelThreePath.size(); i++){
												for(int j = 0; j < levelTwoPath.size(); j++){
													ArrayList path = new ArrayList();
													path.addAll(levelThreePath.get(i));
													path.addAll(levelTwoPath.get(j));
													tempLevelThree.add(path);
												}
											}
											levelThreePath = tempLevelThree;
										}
									//	allPaths.addAll(levelThreePath);
										for(int i = 0; i < levelThreePath.size(); i++){
											levelThreeLeaves++;
											numOfPaths++;
											int analysisRes = analyzePath(levelThreePath.get(i), levelFindLoop);
											if(analysisRes == 0){
												numOfWrongPaths++;
												System.out.println(levelThreePath.get(i));
												output += levelThreePath.get(i)+"\n";
												System.out.println("This path is wrong!");
												output += "This path is wrong!\n";
												res = false;
											}else{
												numOfRightPaths++;
												System.out.println("This path is right!");
												output += "This path is right!\n";
												switch (analysisRes) {
												case 1:
													System.out.println("satisfy pattern 1");
													output += "satisfy pattern 1\n";
													numOfPatternOne++;
													break;
												case 2:
													System.out.println("satisfy pattern 2");
													output += "satisfy pattern 1\n";
													numOfPatternTwo++;
													break;
												case 3:
													System.out.println("satisfy pattern 3");
													output += "satisfy pattern 1\n";
													numOfPatternThree++;
													break;
												case 4:
													System.out.println("satisfy pattern 4");
													output += "satisfy pattern 1\n";
													numOfPatternFour++;
													break;
												}
											}
										}
										
									}
								}
							}
						}
					}
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			int numberOfPathsWithOneLevel = levelOneCallers;
			int numberOfPathsWithTwoLevel = levelTwoCallers + levelOneLeaves;
			int numberOfPathsWithThreeLevel = levelThreeCallers + levelOneLeaves + levelTwoLeaves;
			System.out.println("The number of paths with one level search: " + numberOfPathsWithOneLevel);
			output += "The number of paths with one level search: " + numberOfPathsWithOneLevel + "\n";
			System.out.println("The number of paths with two level search: " + numberOfPathsWithTwoLevel);
			output += "The number of paths with two level search: " + numberOfPathsWithTwoLevel + "\n";
			System.out.println("The number of paths with three level search: " + numberOfPathsWithThreeLevel);
			output += "The number of paths with three level search: " + numberOfPathsWithThreeLevel + "\n";
			
			System.out.println("The number of loops found in level 1: " + levelFindLoop[0]);
			output += "The number of loops found in level 1: " + levelFindLoop[0]+ "\n";
			System.out.println("The number of loops found in level 2: " + levelFindLoop[1]);
			output += "The number of loops found in level 2: " + levelFindLoop[0]+ "\n";
			System.out.println("The number of loops found in level 3: " + levelFindLoop[2]);
			output += "The number of loops found in level 3: " + levelFindLoop[2]+ "\n";
		
			System.out.println("The number of paths found bounded by pattern numeral literal: " + numOfPatternOne);
			output += "The number of paths found bounded by pattern numeral literal: " + numOfPatternOne + "\n";
			System.out.println("The number of paths found bounded by pattern final variable: " + numOfPatternTwo);
			output += "The number of paths found bounded by pattern final variable: " + numOfPatternTwo + "\n";
			System.out.println("The number of paths found bounded by pattern unupdated variable: " + numOfPatternThree);
			output += "The number of paths found bounded by pattern unupdated variable: " + numOfPatternThree + "\n";
			System.out.println("The number of paths found bounded by pattern no loop: " + numOfPatternFour);
			output += "The number of paths found bounded by pattern no loop: " + numOfPatternFour + "\n";
			
		}else{
			System.out.println("not a project, please select a project");
			return null;
		}
		if(!res){
			System.out.println("wrong project!");
			output += "wrong project!\n";
		}else{
			System.out.println("right project!");
			output += "right project!\n";
		}
		System.out.println("Number of Paths: " + numOfPaths);
		output += "Number of Paths: " + numOfPaths+ "\n";
		System.out.println("Number of Wrong Paths: " + numOfWrongPaths);
		output += "Number of Wrong Paths: " + numOfWrongPaths+ "\n";
		System.out.println("Number of Right Paths: " + numOfRightPaths);
		output += "Number of Right Paths: " + numOfRightPaths+ "\n";
		BufferedWriter bw;
		try {
			bw = new BufferedWriter(new FileWriter("C:\\Users\\Robert\\Desktop\\"+projectName));
			bw.write(output);
			bw.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//record time
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		String cost = String.format("%d min, %d sec", 
			    TimeUnit.MILLISECONDS.toMinutes(duration),
			    TimeUnit.MILLISECONDS.toSeconds(duration) - 
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))
		);
		System.out.println("That took " + cost);
		output += "That took " + cost + "\n";
		return null;
	}
	
	/**
	 * get all the subclasses for class thread.
	 */
	private List<IType> getSubClasses(IProject project){
		List<IType> res = new ArrayList<IType>();
		try {
			// Check if we have a Java project
			if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
				IJavaProject javaProject = JavaCore.create(project);
				IType typeThread = javaProject.findType("java.lang.Thread");
				res.add(typeThread);
				IPackageFragment[] packages = javaProject.getPackageFragments();
				for (IPackageFragment mypackage : packages) {
					// Package fragments include all packages in the
					// classpath
					// We will only look at the package from the source
					// folder
					// K_BINARY would include also included JARS, e.g.
					// rt.jar
					if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE){
						for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
							IType[] allTypes = unit.getAllTypes();
							for (IType type : allTypes) {
								ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
								IType[] superTypes = hierarchy.getAllSupertypes(type);
								for (IType superType : superTypes) {
									if (superType.getElementName().equals("Thread")){
										res.add(type);
									//	System.out.println(type.getElementName() + " is a subclass of Thread");
									}
								}
							}
						}
					}
				}
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	private HashSet<IMethod> getCallersOf(IMethod m) {
		CallHierarchy callHierarchy = CallHierarchy.getDefault();
		IMember[] members = { m };
		MethodWrapper[] methodWrappers = callHierarchy.getCallerRoots(members);
		HashSet<IMethod> callers = new HashSet<IMethod>();
		for (MethodWrapper mw : methodWrappers) {
			MethodWrapper[] mw2 = mw.getCalls(new NullProgressMonitor());
			HashSet<IMethod> temp = getIMethods(mw2);
			callers.addAll(temp);
		}
		return callers;
	}
	private HashSet<IMethod> getIMethods(MethodWrapper[] methodWrappers) {
		HashSet<IMethod> c = new HashSet<IMethod>();
		for (MethodWrapper m : methodWrappers) {
			IMethod im = getIMethodFromMethodWrapper(m);
			if (im != null) {
				c.add(im);
			}
		}
		return c;
	}

	private IMethod getIMethodFromMethodWrapper(MethodWrapper m) {
		try {
			IMember im = m.getMember();
			if (im.getElementType() == IJavaElement.METHOD) {
				return (IMethod) m.getMember();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * get the content of a method from IMethod
	 */
	private MethodDeclaration getMethodDeclarationFromIMethod(IProject project, IMethod iMethod) {
		IJavaProject javaProject = JavaCore.create(project);
		IPackageFragment[] packages;
		try {
			packages = javaProject.getPackageFragments();
			for (IPackageFragment mypackage : packages) {
				try {
					if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
						for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
							CompilationUnit parse = parse(unit);
							MethodVisitor visitor = new MethodVisitor();
							parse.accept(visitor);
							for (MethodDeclaration methodDecclaration : visitor.getMethods()) {
								if (iMethod.equals(getIMethodFromMethodDeclaration(methodDecclaration))) {
									return methodDecclaration;
								}
							}
						}
					} 
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (JavaModelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}
	
	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null); // parse
	}
	
	private IMethod getIMethodFromMethodDeclaration(MethodDeclaration methodDeclaration) {
		//IMethod method = (IMethod) methodDeclaration.resolveBinding().getJavaElement();
		IMethodBinding methodBinding = methodDeclaration.resolveBinding();
		if(methodBinding==null){
			return null;
		}
		IMethod method = (IMethod)methodBinding.getJavaElement();
		return method;
	}
	
	private List<List> findPath(Object root, IMethod callee, int level) {
		List<List> paths = new ArrayList<List>();
		if (root == null){
			return paths;
		}
			
		List path = new ArrayList();
		if(root instanceof ASTNode){
			if (isASTNodeCallee((ASTNode)root, callee)) {//This ASTNode invocate the callee
				WrapperASTNode node = new WrapperASTNode((ASTNode)root, level);
				path.add(node);
				paths.add(path);
				return paths;
			}
			List children = getChildren((ASTNode)root);
			if (children.size() == 0) {
				return paths;
			}
			List[] childrenPath = new List[children.size()];
			for (int i = 0; i < children.size(); i++) {
				childrenPath[i] = new ArrayList<List>();
			}
			for (int i = 0; i < children.size(); i++) {
				childrenPath[i] = findPath(children.get(i), callee, level);
			}
			for (int i = 0; i < children.size(); i++) {
				for(int j = 0; j < childrenPath[i].size(); j++){
					WrapperASTNode node = new WrapperASTNode((ASTNode)root, level);
					((ArrayList)(childrenPath[i].get(j))).add(0,node);
					paths.add((ArrayList)(childrenPath[i].get(j)));
				}
			}
		}else if(root instanceof List){
			for(int i =0; i < ((List)root).size(); i++){
				List oneListPath = findPath(((List)root).get(i), callee, level);
				if(oneListPath.size()!=0){
					paths.addAll(oneListPath);
				}
			}	
		}
		return paths;
	}
	
	private boolean isASTNodeCallee(ASTNode root, IMethod callee){
		if(root.getNodeType()== ASTNode.METHOD_INVOCATION){
			IMethodBinding resolvedMethodBinding = ((MethodInvocation)root).resolveMethodBinding();
			if(resolvedMethodBinding == null){
				return false;
			}
			IJavaElement element = resolvedMethodBinding.getJavaElement();
			IMethod iMethodroot = (IMethod)element;
			
			if(iMethodroot == null){
				return false;
			}
			if(iMethodroot.equals(callee)){
				return true;
			}else if(iMethodroot.isSimilar(callee)){//dynamic binding
				//superclasses
				IType type = iMethodroot.getDeclaringType();
				try {
					ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
					IType[] superTypes = hierarchy.getAllSupertypes(type);
					for(int i = 0; i < superTypes.length; i++){
						IMethod[] methodsInSuperType = superTypes[i].getMethods();
						for(int j = 0; j < methodsInSuperType.length; j++){
							if(methodsInSuperType[j].equals(callee)){
								return true;
							}
						}
					}
					
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				//subclasses
				IJavaProject project = iMethodroot.getJavaProject();
				List<IType> subclasses = getSubClasses(project.getProject(), iMethodroot.getDeclaringType());
				for(int i = 0; i < subclasses.size(); i++){
					try {
						IMethod[] methodsInSubType = subclasses.get(i).getMethods();
						for(int j = 0; j < methodsInSubType.length; j++){
							if(methodsInSubType[j].equals(callee)){
								return true;
							}
						}
					} catch (JavaModelException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
			
		}else if(root.getNodeType()== ASTNode.CLASS_INSTANCE_CREATION ){
			IMethodBinding resolvedCreationBinding = ((ClassInstanceCreation)root).resolveConstructorBinding();
			if(resolvedCreationBinding == null){
				return false;
			}
			IJavaElement element = resolvedCreationBinding.getJavaElement();
			IMethod iMethodroot = (IMethod)element;
			
			if(iMethodroot == null){
				return false;
			}
			if(iMethodroot.equals(callee)){
				return true;
			}
		}
		return false;
	}
	
	private List getChildren(ASTNode node) {
		List children = new ArrayList();
	    List list= node.structuralPropertiesForType();
	    for (int i= 0; i < list.size(); i++) {
	        StructuralPropertyDescriptor curr= (StructuralPropertyDescriptor) list.get(i);
	            Object child= node.getStructuralProperty(curr);
	        if (child instanceof List) {
	        	children.addAll((List)child);
	        } else if (child instanceof ASTNode) {
	            children.add(child);
	        }
	        
	    }
	    return children;
	}
	
	/**
	 * get all the subclasses for the class type.
	 */
	private List<IType> getSubClasses(IProject project, IType targetType){
		List<IType> res = new ArrayList<IType>();
		try {
			// Check if we have a Java project
			if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
				IJavaProject javaProject = JavaCore.create(project);
				
				IPackageFragment[] packages = javaProject.getPackageFragments();
				for (IPackageFragment mypackage : packages) {
					// Package fragments include all packages in the
					// classpath
					// We will only look at the package from the source
					// folder
					// K_BINARY would include also included JARS, e.g.
					// rt.jar
					if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE){
						
						for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
							
							IType[] allTypes = unit.getAllTypes();
							for (IType type : allTypes) {
								ITypeHierarchy hierarchy = type.newSupertypeHierarchy(null);
								IType[] superTypes = hierarchy.getAllSupertypes(type);
								for (IType superType : superTypes) {
									if (superType.equals(targetType)){
										res.add(type);
									}
								}
							}
						}
					}
				}
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	
	private int analyzePath(List<WrapperASTNode> path, int[] levelFindLoop){
		boolean flag = false;
		// for multiple loop in one path;
		List<List> allconditions = new ArrayList<List>();
		WhileStatement whileState = null;
		DoStatement doState = null;
		ForStatement forState = null;
		List<ASTNode> scopes = new ArrayList<ASTNode>();

		
		for(int i = 0; i < path.size(); i++){
			if(path.get(i).getNode().getNodeType()==ASTNode.WHILE_STATEMENT ){
				levelFindLoop[path.get(i).getLevel()-1]++;
				flag = true;
				whileState = (WhileStatement)path.get(i).getNode();
				
				List oneconditions = new ArrayList();
				oneconditions.add(((WhileStatement)path.get(i).getNode()).getExpression());
				allconditions.add(oneconditions);
				scopes.add(whileState);
				
				
			}else if(path.get(i).getNode().getNodeType()==ASTNode.DO_STATEMENT){
				levelFindLoop[path.get(i).getLevel()-1]++;
				flag = true;
				doState = (DoStatement)path.get(i).getNode();
				
				
				List oneconditions = new ArrayList();
				oneconditions.add(((DoStatement)path.get(i).getNode()).getExpression());
				allconditions.add(oneconditions);
				scopes.add(doState);
				
			} else if (path.get(i).getNode().getNodeType()==ASTNode.FOR_STATEMENT) {
				levelFindLoop[path.get(i).getLevel()-1]++;
				flag = true;
				forState = (ForStatement)path.get(i).getNode();
				
				List oneconditions = new ArrayList();
				oneconditions.add(forState.getExpression());
				allconditions.add(oneconditions);
				scopes.add(forState);
				
				
			}
			
			
			if(flag){
				if(path.get(i).getNode().getNodeType()==ASTNode.IF_STATEMENT){
					List conditionsForCurLoop = allconditions.get(allconditions.size()-1);
					conditionsForCurLoop.add(((IfStatement)path.get(i).getNode()).getExpression());
				}
			}
		}
		// no loop statements
		if(!flag){
			//conclude this path has bound, due to no loop
			return 4;
		}
		// expand conditions, ex: while(a<b && c<d ) to a<b, c<d

//		conditions = expandConditions(conditions);
		
		int boundPattern = 0;
		
		for (int i = 0; i < allconditions.size(); i++) {
			List conditionsForCurLoop = allconditions.get(i);
			for(int j = 0; j < conditionsForCurLoop.size();j++){
				Expression exp = (Expression)conditionsForCurLoop.get(j);
				int isConditionBound = checkCondition(exp, scopes.get(i));
				if (isConditionBound>0) {
					//conclude this path has bound with the value returned by isConditionBound
					boundPattern = isConditionBound;
					break;
				} 
				// if cur loop no bound
				if (j == conditionsForCurLoop.size() - 1) {
					return 0;
				}
			}
		}
		
//		for(int i = 0; i < conditions.size();i++){
//			Expression exp = (Expression)conditions.get(i);
//			int isConditionBound = checkCondition(exp, scope);
//			if (isConditionBound>0) {
//				//conclude this path has bound with the value returned by isConditionBound
//				return isConditionBound;
//			} 
//		}
		// return the most inner bound pattern
		return boundPattern;
	}
	
	private Boolean findVariable(Object root, IVariableBinding variable) {
		if (root == null){
			return false;
		}
		if(root instanceof ASTNode){
			if (isASTNodeVariable((ASTNode)root, variable)) {//This ASTNode invocate the callee
				return true;
			}
			List children = getChildren((ASTNode)root);
			if (children.size() == 0) {
				return false;
			}

			for (int i = 0; i < children.size(); i++) {
				if(findVariable(children.get(i), variable)){
					return true;
				}
			}
		}else if(root instanceof List){
			for(int i =0; i < ((List)root).size(); i++){
				if(findVariable(((List)root).get(i), variable)){
					return true;
				}
			}
			
		}
		return false;
	}
	
	private boolean isASTNodeVariable(ASTNode root, IBinding variable){
		if(root.getNodeType()== ASTNode.ASSIGNMENT){
			Expression left = ((Assignment)root).getLeftHandSide();
			
			if(left.getNodeType()==ASTNode.SIMPLE_NAME){
				if((((SimpleName)left).resolveBinding()).equals(variable)){
					return true;
				}
			}
		}
		return false;
	}
	
	/*
	* variable updated by a method or "xxx - xxx", return true;
	* else return false;
	*/
	private boolean checkAllReferences(Object root, IVariableBinding variable) {
		if (root == null){
			return false;
		}
		if(root instanceof ASTNode){
			if (isComplicatedUpdate((ASTNode)root, variable)) {//This ASTNode invocate the callee
				return true;
			}
			List children = getChildren((ASTNode)root);
			if (children.size() == 0) {
				return false;
			}

			for (int i = 0; i < children.size(); i++) {
				if(checkAllReferences(children.get(i), variable)){
					return true;
				}
			}
		}else if(root instanceof List){
			for(int i =0; i < ((List)root).size(); i++){
				if(checkAllReferences(((List)root).get(i), variable)){
					return true;
				}
			}
				
		}
		return false;
	}
		
	/*
	* variable updated by a method or "xxx - xxx", return true;
	* else return false;
	*/
	private boolean isComplicatedUpdate(ASTNode root, IBinding variable){
		if(root.getNodeType()== ASTNode.ASSIGNMENT){
			Expression left = ((Assignment)root).getLeftHandSide();
				
			if(left.getNodeType()==ASTNode.SIMPLE_NAME){
				if((((SimpleName)left).resolveBinding()).equals(variable)){
					Expression right = ((Assignment)root).getRightHandSide();
//					if(right == method || right == XXX - XXX){
//						return true;
//					}
					if(right.getNodeType()==ASTNode.METHOD_INVOCATION){
						return true;
					}else if(right.getNodeType()==ASTNode.EXPRESSION_STATEMENT){
						Expression exp = (Expression)right;
						if(exp instanceof InfixExpression){
							if(((InfixExpression)exp).getOperator()==Operator.MINUS){
								return true;
							}
						}
					}
						
				}
			}
		}else if(root.getNodeType()== ASTNode.VARIABLE_DECLARATION_FRAGMENT){

			SimpleName left = ((VariableDeclarationFragment)root).getName();
				
				
				if(((left).resolveBinding()).equals(variable)){
					Expression right = ((VariableDeclarationFragment)root).getInitializer();
//					if(right == method || right == XXX - XXX){
//						return true;
//					}
					if(right.getNodeType()==ASTNode.METHOD_INVOCATION){
						return true;
					}else if(right.getNodeType()==ASTNode.EXPRESSION_STATEMENT){
						Expression exp = (Expression)right;
						if(exp instanceof InfixExpression){
							if(((InfixExpression)exp).getOperator()==Operator.MINUS){
								return true;
							}
						}
					}
						
				}
				
		}
		return false;
	}
	
	private List expandConditions(List originConditions){
		ArrayList res = new ArrayList();
		for(int i = 0; i < originConditions.size();i++){
			Expression exp = (Expression)originConditions.get(i);
			List expConditions = analysisExpression(exp);
			res.addAll(expConditions);
		}
		return res;
	}
	
	private List analysisExpression(Expression exp){
		ArrayList res = new ArrayList();
		if(exp instanceof InfixExpression){
			if(((InfixExpression)exp).getOperator()==InfixExpression.Operator.CONDITIONAL_AND ){
				Expression left = ((InfixExpression)exp).getLeftOperand();
				List leftres = analysisExpression(left);
				res.addAll(leftres);
				Expression right = ((InfixExpression)exp).getRightOperand();
				List rightres = analysisExpression(right);
				res.addAll(rightres);
			}else if(((InfixExpression)exp).getOperator()==InfixExpression.Operator.LESS || ((InfixExpression)exp).getOperator()==InfixExpression.Operator.LESS_EQUALS){
				res.add(exp);
			}
		}
		return res;
	}
	
	private int checkCondition(Expression exp, ASTNode scope) {
		if (exp instanceof InfixExpression){ // Under and expression, if either subexpression matches our pattern, this expression has a bound.
			if(((InfixExpression)exp).getOperator()==InfixExpression.Operator.CONDITIONAL_AND ){
				Expression left = ((InfixExpression)exp).getLeftOperand();
				Expression right = ((InfixExpression)exp).getRightOperand();
				//return checkCondition(left, scope) || checkCondition(right, scope);
				return Math.max(checkCondition(left, scope), checkCondition(right, scope));
			} else if(((InfixExpression)exp).getOperator()==InfixExpression.Operator.CONDITIONAL_OR ){ // Under or expression, only when both of the subexpression matches our pattern, this expression has bound
				Expression left = ((InfixExpression)exp).getLeftOperand();
				Expression right = ((InfixExpression)exp).getRightOperand();
				//return checkCondition(left, scope) && checkCondition(right, scope);
				int leftRes = checkCondition(left, scope);
				int rightRes = checkCondition(right, scope);
				if (leftRes == 0 || rightRes == 0) {
					return 0;
				} else {
					return Math.max(leftRes, rightRes);
				}
			}
			
		}
		
		if(exp instanceof InfixExpression){
			if(((InfixExpression)exp).getOperator()==InfixExpression.Operator.LESS || ((InfixExpression)exp).getOperator()==InfixExpression.Operator.LESS_EQUALS){
				Expression left = ((InfixExpression)exp).getLeftOperand();
				boolean isLeftGood = true;
				if(left.getNodeType() != ASTNode.SIMPLE_NAME){
					isLeftGood = true;
				}else{
					IVariableBinding leftVariable = (IVariableBinding)(((SimpleName)left).resolveBinding());
					if(checkAllReferences(scope, leftVariable)){
						isLeftGood = false;
					}
				}
				if(((InfixExpression)exp).getRightOperand().getNodeType()==ASTNode.NUMBER_LITERAL){
					if(isLeftGood){
						//number literal
						return 1;
					}
				}else if(((InfixExpression)exp).getRightOperand().getNodeType()==ASTNode.SIMPLE_NAME){
					IBinding varRef = ((SimpleName)(((InfixExpression)exp).getRightOperand())).resolveBinding();
					int modifiers = varRef.getModifiers();
					if(Modifier.isFinal(modifiers)){
						if(isLeftGood){
							//final variable
							return 2;
						}
					}else if(!findVariable(scope,(IVariableBinding)varRef)){
						if(isLeftGood){
							// variable not updated in the loop
							return 3;
						}
					}
				} else if (((InfixExpression)exp).getRightOperand().getNodeType()==ASTNode.FIELD_ACCESS) {
					IVariableBinding varRef = ((FieldAccess)(((InfixExpression)exp).getRightOperand())).resolveFieldBinding();
					int modifiers = varRef.getModifiers();
					if(Modifier.isFinal(modifiers)){
						if(isLeftGood){
							//final field variable
							return 2;
						}
					}else if(!findVariable(scope,varRef)){
						if(isLeftGood){
							// field variable not updated in the loop
							return 3;
						}
					}
				} else if (((InfixExpression)exp).getRightOperand().getNodeType()==ASTNode.QUALIFIED_NAME) { //array.length
					QualifiedName qn = (QualifiedName)((InfixExpression)exp).getRightOperand();
					SimpleName sm = qn.getName();
					IBinding varRef = sm.resolveBinding();
					int modifiers = varRef.getModifiers();
					if(Modifier.isFinal(modifiers)){
						if(isLeftGood){
							//final variable
							return 2;
						}
					}else if(!findVariable(scope,(IVariableBinding)varRef)){
						if(isLeftGood){
							// variable not updated in the loop
							return 3;
						}
					}
				} 
			} else if(((InfixExpression)exp).getOperator()==InfixExpression.Operator.GREATER || ((InfixExpression)exp).getOperator()==InfixExpression.Operator.GREATER_EQUALS){
				Expression right = ((InfixExpression)exp).getRightOperand();
				boolean isRightGood = true;
				if(right.getNodeType() != ASTNode.SIMPLE_NAME){
					isRightGood = true;
				}else{
					IVariableBinding rightVariable = (IVariableBinding)(((SimpleName)right).resolveBinding());
					if(checkAllReferences(scope, rightVariable)){
						isRightGood = false;
					}
				}
				if(((InfixExpression)exp).getLeftOperand().getNodeType()==ASTNode.NUMBER_LITERAL){
					if(isRightGood){
						//number literal
						return 1;
					}
				}else if(((InfixExpression)exp).getLeftOperand().getNodeType()==ASTNode.SIMPLE_NAME){
					IBinding varRef = ((SimpleName)(((InfixExpression)exp).getLeftOperand())).resolveBinding();
					int modifiers = varRef.getModifiers();
					if(Modifier.isFinal(modifiers)){
						if(isRightGood){
							//final variable
							return 2;
						}
					}else if(!findVariable(scope,(IVariableBinding)varRef)){
						if(isRightGood){
							// variable not updated in the loop
							return 3;
						}
					}
				} else if (((InfixExpression)exp).getLeftOperand().getNodeType()==ASTNode.FIELD_ACCESS) {
					IVariableBinding varRef = ((FieldAccess)(((InfixExpression)exp).getLeftOperand())).resolveFieldBinding();
					int modifiers = varRef.getModifiers();
					if(Modifier.isFinal(modifiers)){
						if(isRightGood){
							//final field variable
							return 2;
						}
					}else if(!findVariable(scope,varRef)){
						if(isRightGood){
							// field variable not updated in the loop
							return 3;
						}
					}
				} else if (((InfixExpression)exp).getLeftOperand().getNodeType()==ASTNode.QUALIFIED_NAME) { //array.length
					QualifiedName qn = (QualifiedName)((InfixExpression)exp).getLeftOperand();
					SimpleName sm = qn.getName();
					IBinding varRef = sm.resolveBinding();
					int modifiers = varRef.getModifiers();
					if(Modifier.isFinal(modifiers)){
						if(isRightGood){
							//final variable
							return 2;
						}
					}else if(!findVariable(scope,(IVariableBinding)varRef)){
						if(isRightGood){
							// variable not updated in the loop
							return 3;
						}
					}
				} 
			}
		}
		return 0;
	}
}
