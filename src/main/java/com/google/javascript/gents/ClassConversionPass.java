package com.google.javascript.gents;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts ES5 JavaScript classes into ES6 JavaScript classes. Prototype declarations are
 * converted into the new class definitions of ES6.
 */
public final class ClassConversionPass extends AbstractPostOrderCallback implements CompilerPass {

  static final DiagnosticType GENTS_CLASS_REDEFINED_ERROR = DiagnosticType.error(
      "GENTS_CLASS_REDEFINED_ERROR",
      "The class {0} has been defined multiple times within the same file.");
  static final DiagnosticType GENTS_UNKNOWN_CLASS_ERROR = DiagnosticType.error(
      "GENTS_UNKNOWN_CLASS_ERROR",
      "The class {0} could not be found.");

  private final AbstractCompiler compiler;
  private Map<String, Node> classes;

  public ClassConversionPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.classes = new LinkedHashMap<>();
  }

  @Override
  public void process(Node externs, Node root) {
    for (Node child : root.children()) {
      // We convert each file independently to avoid merging class methods from different files.
      if (child.isScript()) {
        this.classes = new LinkedHashMap<>();
        NodeTraversal.traverseEs6(compiler, child, this);
      }
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.CLASS:
        addClassToScope(n);
        break;
      case Token.FUNCTION:
        JSDocInfo bestJSDocInfo = NodeUtil.getBestJSDocInfo(n);
        if (bestJSDocInfo != null && bestJSDocInfo.isConstructor()) {
          constructorToClass(n, bestJSDocInfo);
        }
        break;
      case Token.EXPR_RESULT:
        ClassMemberDeclaration declaration = new ClassMemberDeclaration(n);
        if (declaration.isValid()) {
          // TODO(renez): extend this to handle fields as well
          if (declaration.rhs.isFunction()) {
            maybeMoveMethodsIntoClasses(declaration);
          }
        }
        break;
      default:
        break;
    }
  }

  /**
   * Converts @constructor annotated functions into class definitions.
   */
  void constructorToClass(Node n, JSDocInfo jsDoc) {
    String className = NodeUtil.getNearestFunctionName(n);
    // Break up function
    Node name = n.getFirstChild();
    Node params = n.getSecondChild();
    Node body = n.getLastChild();
    n.detachChildren();

    // The empty name corresponds to anonymous constructors.
    // The name is usually located in the surrounding context.
    // ie. /** @constructor */ var A = function() {};
    // is converted to: var A = class {};
    if (name.getString().equals("")) {
      name = IR.empty();
    }

    // Superclass defaults to empty
    Node superClass = IR.empty();
    if (jsDoc.getBaseType() != null) {
      // Fullname of superclass
      // Closure Compiler generates non-nullable base classes:
      // ie. A.B.C is parsed as !A.B.C
      String superClassName = jsDoc
          .getBaseType()
          .getRoot()
          .getFirstChild() // ignore the ! node as we always output non nullable types
          .getString();
      superClass = getProp(superClassName);
    }

    // TODO(renez): traverse function body to pull out field declaration info

    // Generate new class node with only a constructor method
    Node constructor = IR.memberFunctionDef(
        "constructor",
        IR.function(IR.name(""), params, body)
    );
    // Sets jsdoc info to preserve type declarations on method
    constructor.setJSDocInfo(jsDoc);

    Node classMembers = new Node(Token.CLASS_MEMBERS, constructor);
    Node classNode = new Node(Token.CLASS, name, superClass, classMembers);

    n.getParent().replaceChild(n, classNode);
    compiler.reportCodeChange();

    addClassToScope(className, classNode);
  }

  /**
   * Attempts to move a method declaration into a class definition. This generates a new
   * MEMBER_FUNCTION_DEF Node while removing the old function node from the AST.
   *
   * This fails to move a method declaration when referenced class does not exist in scope.
   */
  void maybeMoveMethodsIntoClasses(ClassMemberDeclaration declaration) {
    if (!classes.containsKey(declaration.getClassName())) {
      // Only emit error on non-static (prototype) methods.
      // This is because we can assign to non-class objects such as records.
      if (!declaration.isStatic()) {
        compiler.report(JSError.make(
            declaration.fullName,
            GENTS_UNKNOWN_CLASS_ERROR,
            declaration.getClassName()
        ));
      }
      return;
    }

    Node classNode = classes.get(declaration.getClassName());
    Node classMembers = classNode.getLastChild();

    // Detach nodes in order to move them around in the AST.
    declaration.exprRoot.detachFromParent();
    declaration.rhs.detachFromParent();

    Node memberFunc = IR.memberFunctionDef(declaration.getMemberName(), declaration.rhs);
    memberFunc.setStaticMember(declaration.isStatic());
    memberFunc.setJSDocInfo(declaration.jsDoc);

    // Append the new method to the class
    classMembers.addChildToBack(memberFunc);
    compiler.reportCodeChange();
  }

  /**
   * Adds a class node to the top level scope.
   *
   * This determines the classname using the nearest available name node.
   */
  void addClassToScope(Node n) {
    String className = NodeUtil.getName(n);
    if (className == null) {
      // We do not emit an error here as there can be anonymous classes without names.
      return;
    }
    addClassToScope(className, n);
  }

  /**
   * Adds a class node to the top level scope.
   */
  void addClassToScope(String className, Node n) {
    if (classes.containsKey(className)) {
      compiler.report(JSError.make(n, GENTS_CLASS_REDEFINED_ERROR, className));
      return;
    }
    classes.put(className, n);
  }

  /**
   * Converts a qualified name string into a tree of GETPROP.
   *
   * ex. "foo.bar.baz" is converted to GETPROP(GETPROP(NAME(foo), STRING(bar)), STRING(baz)).
   */
  static Node getProp(String fullname) {
    Iterator<String> propList = Splitter.on('.').split(fullname).iterator();
    Node root = IR.name(propList.next());
    if (!propList.hasNext()) {
      return root;
    }
    return IR.getprop(root, propList.next(), Iterators.toArray(propList, String.class));
  }

  /**
   * Represents an assignment to a class member.
   */
  private final class ClassMemberDeclaration {
    Node exprRoot;
    Node assignNode;
    Node fullName;
    Node rhs;
    JSDocInfo jsDoc;

    ClassMemberDeclaration(Node n) {
      this.exprRoot = n;
      this.assignNode = n.getFirstChild();
      this.fullName = assignNode.getFirstChild();
      this.rhs = assignNode.getLastChild();
      this.jsDoc = NodeUtil.getBestJSDocInfo(n);
    }

    boolean isValid() {
      return assignNode.isAssign() && fullName.isGetProp();
    }

    boolean isStatic() {
      if (NodeUtil.isPrototypePropertyDeclaration(exprRoot)) {
        if (fullName.getFirstChild().getLastChild().getString().equals("prototype")) {
          return false;
        }
      }
      return true;
    }

    /**
     * Gets the full class name of this declaration.
     * ex. A.B.C.prototype.foo -> A.B.C
     * ex. A.B.C.D.bar -> A.B.C.D
     */
    String getClassName() {
      Node n = fullName;
      if (isStatic()) {
        return n.getFirstChild().getQualifiedName();
      }
      while (n.isGetProp()) {
        if (n.getLastChild().getString().equals("prototype")) {
          return n.getFirstChild().getQualifiedName();
        }
        n = n.getFirstChild();
      }
      throw new IllegalArgumentException("Invalid declaration name: " + n.toStringTree());
    }

    /**
     * Gets the name of the method this defines.
     */
    String getMemberName() {
      if (fullName.isGetProp()) {
        return fullName.getLastChild().getString();
      }
      throw new IllegalArgumentException("Invalid declaration name: " + fullName.toStringTree());
    }
  }

}
