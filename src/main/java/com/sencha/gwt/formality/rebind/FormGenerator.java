package com.sencha.gwt.formality.rebind;

import java.io.PrintWriter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.sencha.gwt.formality.client.Form.Label;
import com.sencha.gxt.widget.core.client.container.FlowLayoutContainer;
import com.sencha.gxt.widget.core.client.form.FieldLabel;

public class FormGenerator extends Generator {

  @Override
  public String generate(TreeLogger logger, GeneratorContext context, String typeName) throws UnableToCompleteException {
    TypeOracle oracle = context.getTypeOracle();

    JClassType editorType = oracle.findType(Editor.class.getName());
    JClassType toGenerate = oracle.findType(typeName);

    String packageName = toGenerate.getPackage().getName();
    String simpleSourceName = toGenerate.getName().replace('.', '_') + "Impl";

    // Attempt to create a new file - if null, it already exists, and should be used
    PrintWriter pw = context.tryCreate(logger, packageName, simpleSourceName);
    if (pw == null) {
      return packageName + "." + simpleSourceName;
    }

    // Prepare to make a new class in given package with the specified name
    ClassSourceFileComposerFactory factory =
        new ClassSourceFileComposerFactory(packageName, simpleSourceName);

    // Set the superclass, interfaces, and any imports
    factory.addImplementedInterface(typeName);
    factory.addImport(GWT.class.getName());
    // Create a SourceWrite to write out code
    SourceWriter sw = factory.createSourceWriter(context, pw);

    // Declare a container, that will be returned by asWidget()
    // root field to hold the fields
    sw.println("private %1$s _root;", FlowLayoutContainer.class.getName());

    // Build the fields, methods needed to generate the requested fields and make them available
    for (JMethod m : toGenerate.getOverridableMethods()) {
      assert m.getParameters().length == 0;
      JClassType editor = m.getReturnType().isClassOrInterface();
      if (editor != null && editor.isAssignableTo(editorType)) {
        // field
        sw.println("private %1$s %2$s;", editor.getParameterizedQualifiedSourceName(), m.getName());//field

        // method
        sw.println("%1$s {", m.getReadableDeclaration(false, true, true, true, true));
        sw.indentln("return %1$s;", m.getName());
        sw.println("}");
      }
    }


    // constructor to assemble the fields
    sw.println("public %1$s() {", simpleSourceName);
    sw.indent();

    // Instantiate the root container
    sw.println("this._root = GWT.create(%1$s.class);", FlowLayoutContainer.class.getName());
    for (JMethod m : toGenerate.getOverridableMethods()) {
      if (m.getName().equals("asWidget")) {
        continue;
      }
      logger.log(Type.DEBUG, "Appending " + m.getName());
      JClassType editor = m.getReturnType().isClassOrInterface();
      assert editor != null && editor.isAssignableTo(editorType);//not certain we need to take this much care

      // Instantiate the field
      sw.println("this.%2$s = GWT.create(%1$s.class);", editor.getQualifiedSourceName(), m.getName());

      // look for the label annotation, if it exists
      Label l = m.getAnnotation(Label.class);
      String labelExpr = l == null ? m.getName() : l.value();

      // Check to see if it implements IsWidget, if not, probably just an editor adapter
      if (editor.isAssignableTo(oracle.findType(IsWidget.class.getName()))) {
        // Optionally wrap the field
        // Then append it to the container
        sw.println("_root.add(new %1$s(this.%2$s.asWidget(), \"%3$s\"));", FieldLabel.class.getName(), m.getName(), escape(labelExpr));
      }
    }
    sw.outdent();
    sw.println("}");

    // expose the root field
    sw.println("public %1$s asWidget() {return _root;}", Widget.class.getName());

    sw.commit(logger);

    return factory.getCreatedClassName();
  }

}