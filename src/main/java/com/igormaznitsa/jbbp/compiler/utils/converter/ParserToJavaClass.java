/*
 * Copyright 2017 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.jbbp.compiler.utils.converter;

import com.igormaznitsa.jbbp.JBBPParser;
import com.igormaznitsa.jbbp.compiler.JBBPCompiledBlock;
import com.igormaznitsa.jbbp.compiler.JBBPCompiler;
import com.igormaznitsa.jbbp.compiler.JBBPNamedFieldInfo;
import com.igormaznitsa.jbbp.compiler.tokenizer.JBBPFieldTypeParameterContainer;
import com.igormaznitsa.jbbp.compiler.varlen.JBBPIntegerValueEvaluator;
import com.igormaznitsa.jbbp.io.JBBPBitInputStream;
import com.igormaznitsa.jbbp.io.JBBPBitNumber;
import com.igormaznitsa.jbbp.io.JBBPByteOrder;
import com.igormaznitsa.jbbp.utils.JBBPUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ParserToJavaClass extends AbstractCompiledBlockConverter<ParserToJavaClass> {

    private static final String TEMPLATE;

    static {
        JBBPBitInputStream stream = null;
        try {
            stream = new JBBPBitInputStream(ParserToJavaClass.class.getResourceAsStream("/templates/template_java_class.txt"));
            TEMPLATE = new String(stream.readByteArray(-1), "UTF-8");
        } catch (IOException ex) {
            throw new Error("Can't load template", ex);
        } finally {
            JBBPUtils.closeQuietly(stream);
        }
    }

    private final String packageName;
    private final String className;
    private final TextBuffer imports = new TextBuffer();
    private final TextBuffer methods = new TextBuffer();
    private final TextBuffer staticFields = new TextBuffer();
    private final TextBuffer mainFields = new TextBuffer();
    private final TextBuffer readFields = new TextBuffer();
    private final TextBuffer classComments = new TextBuffer();
    private final TextBuffer constructorBody = new TextBuffer();
    private final List<TextBuffer> embeddedClasses = new ArrayList<TextBuffer>();
    private TextBuffer currentInsideClass;
    private String classModifiers;
    private List<String> constructorArgs = new ArrayList<String>();
    private final AtomicBoolean detectedCustomFields = new AtomicBoolean();
    private final AtomicBoolean detectedVarFields = new AtomicBoolean();
    private final AtomicBoolean detectedExternalFieldsInEvaluator = new AtomicBoolean();
    private final AtomicInteger anonymousFieldCounter = new AtomicInteger();

    private String result;

    public ParserToJavaClass(final String packageName, final String className, final JBBPParser notNullParser, final String nullableExtraMethods) {
        this(packageName, className, notNullParser.getFlags(), notNullParser.getCompiledBlock(), nullableExtraMethods);
    }

    public ParserToJavaClass(final String packageName, final String className, final int parserFlags, final JBBPCompiledBlock notNullCompiledBlock, final String nullableExtraMethods) {
        super(parserFlags,notNullCompiledBlock);
        this.packageName = packageName;
        this.className = className;
    }

    @Override
    public void onConvertStart() {
        this.embeddedClasses.clear();
        this.classModifiers = "public ";
        this.constructorArgs.clear();
        this.mainFields.clean();
        this.staticFields.clean();
        this.constructorBody.clean();
        this.imports.clean();
        this.currentInsideClass = null;
        this.detectedCustomFields.set(false);
        this.detectedExternalFieldsInEvaluator.set(false);
        this.detectedVarFields.set(false);
        this.anonymousFieldCounter.set(1);
        this.classComments.clean().print("// Generated by ").print(ParserToJavaClass.class.getName());
    }

    public String getResult() {
        return this.result;
    }

    @Override
    public void onConvertEnd() {
        if (this.detectedExternalFieldsInEvaluator.get()){
            this.classModifiers += "abstract ";
            this.methods.println("public abstract int getValueForName(String name);");
        }

        if (this.detectedCustomFields.get()) {
            this.imports.println("import com.igormaznitsa.jbbp.JBBPCustomFieldTypeProcessor;");
            this.imports.println("import com.igormaznitsa.jbbp.compiler.tokenizer.JBBPFieldTypeParameterContainer;");
            this.imports.println("import com.igormaznitsa.jbbp.compiler.JBBPNamedFieldInfo;");
            this.imports.println("import com.igormaznitsa.jbbp.model.JBBPAbstractField;");
            this.constructorArgs.add("JBBPCustomFieldTypeProcessor customFieldProcessor");
            this.mainFields.println("protected final JBBPCustomFieldTypeProcessor __cftProcessor; // custom field processor");
            this.constructorBody.println("this.__cftProcessor = customFieldProcessor;");
        }

        this.result = TEMPLATE
                .replace("${classModifiers}",this.classModifiers)
                .replace("${import}", this.imports.toStringAndClean(0))
                .replace("${readFields}", this.readFields.toStringAndClean(4))
                .replace("${methods}", this.methods.toStringAndClean(4))
                .replace("${constructorArgs}", toArgs(this.constructorArgs))
                .replace("${constructorBody}", this.constructorBody.toStringAndClean(4))
                .replace("${mainFields}", this.mainFields.toStringAndClean(2))
                .replace("${staticFields}", this.staticFields.toStringAndClean(2))
                .replace("${className}", this.className)
                .replace("${classComments}", this.classComments.toStringAndClean(0))
                .replace("${packageName}", this.packageName);
    }

    @Override
    public void onStructStart(final int offsetInCompiledBlock, final JBBPNamedFieldInfo nullableNameFieldInfo, final JBBPIntegerValueEvaluator nullableArraySize) {

    }

    @Override
    public void onStructEnd(final int offsetInCompiledBlock, final JBBPNamedFieldInfo nullableNameFieldInfo) {

    }

    @Override
    public void onBitField(final int offsetInCompiledBlock, final JBBPNamedFieldInfo nullableNameFieldInfo, final JBBPIntegerValueEvaluator notNullFieldSize, final JBBPIntegerValueEvaluator nullableArraySize) {
        TextBuffer fieldOut = this.currentInsideClass == null ? this.mainFields : this.currentInsideClass;

        final String fieldName = nullableNameFieldInfo == null ? "_afield" + anonymousFieldCounter.getAndIncrement() : nullableNameFieldInfo.getFieldName();
        final String javaFieldType = "byte";

        String sizeOfField = evaluatorToString(offsetInCompiledBlock, notNullFieldSize,this.detectedExternalFieldsInEvaluator);
        try{
            sizeOfField = "JBBPBitNumber."+JBBPBitNumber.decode(Integer.parseInt(sizeOfField)).name();
        }catch(NumberFormatException ex){
            sizeOfField = "JBBPBitNumber.decode("+sizeOfField+')';
        }


        final String arraySize = nullableArraySize == null ? null : evaluatorToString(offsetInCompiledBlock, nullableArraySize, this.detectedExternalFieldsInEvaluator);

        final String fieldModifier;
        if (nullableNameFieldInfo == null) {
            fieldModifier = "protected ";
        } else {
            fieldModifier = "public ";
        }

        if (arraySize == null) {
            this.readFields.print(fieldName).print(" = theStream.readBitField(").print(sizeOfField).println(");");
        } else {
            this.readFields.print(fieldName).print(" = theStream.readBitsArray(").print(arraySize).print(",").print(sizeOfField).println(");");
        }

        fieldOut.println(nullableNameFieldInfo == null ? "// an anonymous field" : "// the named field '" + nullableNameFieldInfo.getFieldName() + '\'');
        fieldOut.print(fieldModifier).print(javaFieldType).print(" ").print(nullableArraySize == null ? "" : "[] ").print(fieldName).println(";").println();

    }

    @Override
    public void onCustom(final int offsetInCompiledBlock, final JBBPFieldTypeParameterContainer notNullfieldType, final JBBPNamedFieldInfo nullableNameFieldInfo, final JBBPByteOrder byteOrder, final boolean readWholeStream, final JBBPIntegerValueEvaluator nullableArraySizeEvaluator, final JBBPIntegerValueEvaluator extraDataValueEvaluator) {
        this.detectedCustomFields.set(true);

        final String fieldModifier;
        if (nullableNameFieldInfo == null) {
            fieldModifier = "protected ";
        } else {
            fieldModifier = "public ";
        }

        final TextBuffer fieldOut = this.currentInsideClass == null ? this.mainFields : this.currentInsideClass;
        final String fieldName = nullableNameFieldInfo == null ? "_afield" + anonymousFieldCounter.getAndIncrement() : nullableNameFieldInfo.getFieldName();

        fieldOut.println(nullableNameFieldInfo == null ? "// an anonymous field" : "// the named field '" + nullableNameFieldInfo.getFieldName() + '\'');
        fieldOut.print(fieldModifier).print("JBBPAbstractField ").print(fieldName).println(";").println();

        final String jbbpNFI = nullableNameFieldInfo == null ? null : "new JBBPNamedFieldInfo(\""+nullableNameFieldInfo.getFieldName()+"\",\""+nullableNameFieldInfo.getFieldPath()+"\","+nullableNameFieldInfo.getFieldOffsetInCompiledBlock()+")";
        final String jbbpFTPC = "new JBBPFieldTypeParameterContainer(JBBPByteOrder."+notNullfieldType.getByteOrder().name()+","+ toJStr(notNullfieldType.getTypeName())+","+toJStr(notNullfieldType.getExtraData())+")";

        if (jbbpNFI!=null) {
            this.staticFields.print("private static final JBBPNamedFieldInfo __nfi_").print(fieldName).print(" = ").print(jbbpNFI).println(";");
        }
        this.staticFields.print("private static final JBBPFieldTypeParameterContainer __ftpc_").print(fieldName).print(" = ").print(jbbpFTPC).println(";");

        this.readFields
                .print(fieldName)
                .print(" = ")
                .print("this.__cftProcessor.readCustomFieldType(theStream,theStream.getBitOrder()")
                .print(",").print(this.parserFlags)
                .print(",").print("${className}.__ftpc_").print(fieldName)
                .print(",").print(jbbpNFI == null ? "null" : "${className}.__nfi_"+fieldName)
                .print(",").print(extraDataValueEvaluator == null ? "0" : evaluatorToString(offsetInCompiledBlock,extraDataValueEvaluator,this.detectedExternalFieldsInEvaluator))
                .print(",").print(readWholeStream)
                .print(",").print(nullableArraySizeEvaluator == null ? "-1" : evaluatorToString(offsetInCompiledBlock,nullableArraySizeEvaluator, this.detectedExternalFieldsInEvaluator))
                .println(");");
    }

    @Override
    public void onVar(final int offsetInCompiledBlock, final JBBPNamedFieldInfo nullableNameFieldInfo, final JBBPByteOrder byteOrder, final JBBPIntegerValueEvaluator nullableArraySize) {
        this.detectedVarFields.set(true);
    }

    private String evaluatorToString(final int offsetInBlock, final JBBPIntegerValueEvaluator evaluator, final AtomicBoolean detectedExternalField) {
        final StringBuilder buffer = new StringBuilder();

        detectedExternalField.set(false);

        final ExpressionEvaluatorVisitor visitor = new ExpressionEvaluatorVisitor() {
            private final List<Object> stack = new ArrayList<Object>();

            @Override
            public ExpressionEvaluatorVisitor begin() {
                this.stack.clear();
                return this;
            }

            @Override
            public ExpressionEvaluatorVisitor visit(final Special specialField) {
                stack.add(specialField);
                return this;
            }

            @Override
            public ExpressionEvaluatorVisitor visit(final JBBPNamedFieldInfo nullableNameFieldInfo, final String nullableExternalFieldName) {
                if (nullableNameFieldInfo != null) {
                    this.stack.add(nullableNameFieldInfo);
                } else if (nullableExternalFieldName!= null) {
                    detectedExternalField.set(true);
                    this.stack.add(nullableExternalFieldName);
                }
                return this;
            }

            @Override
            public ExpressionEvaluatorVisitor visit(final Operator operator) {
                this.stack.add(operator);
                return this;
            }

            @Override
            public ExpressionEvaluatorVisitor visit(final int value) {
                this.stack.add(value);
                return this;
            }

            private String argToString(final Object obj) {
                if (obj instanceof Special) {
                    switch((Special)obj){
                        case STREAM_COUNTER: return "(int)theStream.getCounter()";
                        default: throw new Error("Unexpected special");
                    }
                } else if (obj instanceof Integer) {
                    return obj.toString();
                } else if (obj instanceof String) {
                    return "this.getValueForName(\""+obj.toString()+"\")";
                } else if (obj instanceof JBBPNamedFieldInfo) {
                    return ((JBBPNamedFieldInfo)obj).getFieldPath();
                }
                throw new Error("Unexpected object : "+obj);
            }

            @Override
            public ExpressionEvaluatorVisitor end() {
                // process operators
                Operator lastOp = null;

                final List<String> values = new ArrayList<String>();

                for(int i=0;i<this.stack.size();i++) {
                    final Object cur = this.stack.get(i);
                    if (cur instanceof Operator) {
                        final Operator op = (Operator)cur;

                        if (lastOp!=null && lastOp.getPriority()<op.getPriority()){
                            buffer.insert(0,'(').append(')');
                        }

                        if (op.getArgsNumber()<=values.size()) {
                            if (op.getArgsNumber() == 1) {
                                buffer.append(op.getText()).append(values.remove(values.size()-1));
                            } else {
                                buffer.append(values.remove(values.size()-2)).append(op.getText()).append(values.remove(values.size()-1));
                            }
                        } else {
                            buffer.append(op.getText()).append(values.remove(values.size()-1));
                        }

                        lastOp = op;
                    } else {
                        values.add(argToString(cur));
                    }
                }

                if (!values.isEmpty()){
                    buffer.append(values.get(0));
                }

                return this;
            }
        };

        evaluator.visit(this.compiledBlock, offsetInBlock, visitor);

        return buffer.toString();
    }

    @Override
    public void onPrimitive(final int offsetInCompiledBlock, final int primitiveType, final JBBPNamedFieldInfo nullableNameFieldInfo, final JBBPByteOrder byteOrder, final JBBPIntegerValueEvaluator nullableArraySize) {
        TextBuffer fieldOut = this.currentInsideClass == null ? this.mainFields : this.currentInsideClass;

        final String fieldName = nullableNameFieldInfo == null ? "_afield" + anonymousFieldCounter.getAndIncrement() : nullableNameFieldInfo.getFieldName();
        final String javaFieldType;

        final String arraySize = nullableArraySize == null ? null : evaluatorToString(offsetInCompiledBlock, nullableArraySize, this.detectedExternalFieldsInEvaluator);

        switch (primitiveType) {
            case JBBPCompiler.CODE_BOOL: {
                javaFieldType = "boolean";
                if (arraySize == null) {
                    this.readFields.print(fieldName).println(" = theStream.readBoolean();");
                } else {
                    this.readFields.print(fieldName).print(" = theStream.readBoolArray(").print(arraySize).println(");");
                }
            }
            break;
            case JBBPCompiler.CODE_BYTE:
            case JBBPCompiler.CODE_UBYTE: {
                javaFieldType = "byte";
                if (arraySize == null) {
                    this.readFields.print(fieldName).println(" = (byte) theStream.readByte();");
                } else {
                    this.readFields.print(fieldName).print(" = theStream.readByteArray(").print(arraySize).println(");");
                    if (byteOrder == JBBPByteOrder.LITTLE_ENDIAN) {
                        this.readFields.print("JBBPUtils.reverseArray(").print(fieldName).println(");");
                    }
                }
            }
            break;
            case JBBPCompiler.CODE_INT: {
                javaFieldType = "int";
                if (arraySize == null) {
                    this.readFields.print(fieldName).print(" = theStream.readInt(JBBPByteOrder.").print(byteOrder.name()).println(");");
                } else {
                    this.readFields.print(fieldName).print(" = theStream.readIntArray(").print(arraySize).print(",JBBPByteOrder.").print(byteOrder.name()).println(");");
                }
            }
            break;
            case JBBPCompiler.CODE_USHORT:
            case JBBPCompiler.CODE_SHORT: {
                javaFieldType = "short";
                if (arraySize == null) {
                    this.readFields.print(fieldName).print(" = (short)theStream.readUnsignedShort(JBBPByteOrder.").print(byteOrder.name()).println(");");
                } else {
                    this.readFields.print(fieldName).print(" = theStream.readShortArray(").print(arraySize).print(",JBBPByteOrder.").print(byteOrder.name()).println(");");
                }
            }
            break;
            case JBBPCompiler.CODE_LONG: {
                javaFieldType = "long";
                if (arraySize == null) {
                    this.readFields.print(fieldName).print(" = theStream.readLong(JBBPByteOrder.").print(byteOrder.name()).println(");");
                } else {
                    this.readFields.print(fieldName).print(" = theStream.readLongArray(").print(arraySize).print(",JBBPByteOrder.").print(byteOrder.name()).println(");");
                }
            }
            break;
            default:
                throw new Error("Unexpected primitive type, contact developer : " + primitiveType);
        }

        final String fieldModifier;
        if (nullableNameFieldInfo == null) {
            fieldModifier = "protected ";
        } else {
            fieldModifier = "public ";
        }

        fieldOut.println(nullableNameFieldInfo == null ? "// an anonymous field" : "// the named field '" + nullableNameFieldInfo.getFieldName() + '\'');
        fieldOut.print(fieldModifier).print(javaFieldType).print(" ").print(nullableArraySize == null ? "" : "[] ").print(fieldName).println(";").println();
    }

    @Override
    public void onActionItem(final int offsetInCompiledBlock, final int actionType, final JBBPIntegerValueEvaluator nullableArgument) {
        final String valueTxt = nullableArgument == null ? null : evaluatorToString(offsetInCompiledBlock, nullableArgument, this.detectedExternalFieldsInEvaluator);

        switch (actionType) {
            case JBBPCompiler.CODE_RESET_COUNTER: {
                this.readFields.println("theStream.resetCounter();");
            }
            break;
            case JBBPCompiler.CODE_ALIGN: {
                this.readFields.print("theStream.align(").print(valueTxt).println(");");
            }
            break;
            case JBBPCompiler.CODE_SKIP: {
                this.readFields.print("theStream.skip(").print(valueTxt).println(");");
            }
            break;
            default: {
                throw new Error("Detected unknown action, contact developer!");
            }
        }
    }

    private static String toArgs(final List<String> args) {
        final StringBuilder result = new StringBuilder();
        for(final String s : args){
            if (result.length()>0) result.append(',');
            result.append(s);
        }
        return result.toString();
    }

    private static String toJStr(final Object obj) {
        return obj == null ? "null" : "\""+obj.toString()+"\"";
    }
}
