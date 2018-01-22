package com.wkp.mvp;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.*;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import java.util.List;

public class CreateInterfaceAction extends BaseGenerateAction {
    private static final String UP_ACTIVITY = "Activity";
    private static final String LOW_ACTIVITY = "activity";
    private static final String UP_FRAGMENT = "Fragment";
    private static final String LOW_FRAGMENT = "fragment";
    private static final String UP_VIEW = "View";
    private static final String LOW_VIEW = "view";
    private static final String INTER = "inter";
    private static final String UP_MODEL = "Model";
    private static final String LOW_MODEL = "model";
    private static final String UP_PRESENTER = "Presenter";
    private static final String LOW_PRESENTER = "presenter";
    private static final String UP_IMPL = "Impl";
    private static final String LOW_IMPL = "impl";
    private static final String UP_CALL_BACK = "CallBack";
    private static final String LOW_CALL_BACK = "callback";
    private Editor mEditor;
    private Project mProject;
    private PsiJavaFile mActivityFile;
    private JavaDirectoryService mDirectoryService;
    private String mName;
    private PsiElementFactory mElementFactory;
    private PsiShortNamesCache mNamesCache;
    private PsiFileFactory mPsiFileFactory;
    private JavaCodeStyleManager mStyleManager;
    private boolean mIsActivity;
    private PsiClass mTargetClass;
    private boolean mHasPresenterInter;
    private PsiField mPresenterInterField;
    private boolean mHasPresenterExpression;
    private PsiElement mBefPresenterElement;
    private PsiElement mSemicolon;
    private PsiElement mWhiteSpace;

    public CreateInterfaceAction() {
        super(null);
    }

    public CreateInterfaceAction(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiJavaFile psiFile = ((PsiJavaFile) e.getData(LangDataKeys.PSI_FILE));
        Presentation presentation = e.getPresentation();
        if (psiFile != null && editor != null) {
            PsiClass targetClass = getTargetClass(editor, psiFile);
            presentation.setEnabledAndVisible(isActivity(targetClass) || isFragment(targetClass));
            return;
        }
        presentation.setEnabledAndVisible(false);
    }

    /**
     * 是否为Activity
     *
     * @param psiClass
     * @return
     */
    private boolean isActivity(PsiClass psiClass) {
        if (psiClass != null) {
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                String name = superClass.getName();
                return UP_ACTIVITY.equals(name) || isActivity(superClass);
            }
        }
        return false;
    }

    /**
     * 是否为Fragment
     *
     * @param psiClass
     * @return
     */
    private boolean isFragment(PsiClass psiClass) {
        if (psiClass != null) {
            PsiClass superClass = psiClass.getSuperClass();
            if (superClass != null) {
                String name = superClass.getName();
                return UP_FRAGMENT.equals(name) || isFragment(superClass);
            }
        }
        return false;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        mEditor = e.getData(PlatformDataKeys.EDITOR);       //编辑器
        mProject = e.getData(PlatformDataKeys.PROJECT);     //项目
        mActivityFile = ((PsiJavaFile) e.getData(LangDataKeys.PSI_FILE));   //当前文件
        mDirectoryService = JavaDirectoryService.getInstance();             //文件夹服务类
        if (mEditor != null && mActivityFile != null && mProject != null) {
            mStyleManager = JavaCodeStyleManager.getInstance(mProject);     //代码管理类
            mPsiFileFactory = PsiFileFactory.getInstance(mProject);         //文件工厂
            mNamesCache = PsiShortNamesCache.getInstance(mProject);         //短类名缓存
            mElementFactory = JavaPsiFacade.getInstance(mProject).getElementFactory();  //代码元素工厂
            mTargetClass = getTargetClass(mEditor, mActivityFile);          //当前编辑类
            if (mTargetClass == null) {
                return;
            }
            //处理类名
            String startName = mTargetClass.getName();
            if (isActivity(mTargetClass)) {
                mIsActivity = true;
                mName = startName.replaceAll(UP_ACTIVITY, "").replaceAll(LOW_ACTIVITY, "") + "A";
            } else if (isFragment(mTargetClass)) {
                mIsActivity = false;
                mName = startName.replaceAll(UP_FRAGMENT, "").replaceAll(LOW_FRAGMENT, "") + "F";
            }
            //获取文件夹
            PsiDirectory currentDirectory = mActivityFile.getParent();
            if (currentDirectory != null) {
                PsiDirectory originalDirectory = getOriginalDirectory(currentDirectory);
                createAllInterAndImpl(originalDirectory);
            }
        }
    }

    /**
     * 创建所有的interface
     *
     * @param originalDirectory
     */
    private void createAllInterAndImpl(PsiDirectory originalDirectory) {
        WriteCommandAction.runWriteCommandAction(mProject, new Runnable() {
            @Override
            public void run() {
                //创建inter
                PsiDirectory viewDirectory = createMvpDirectory(LOW_VIEW, originalDirectory);
                PsiClass viewInter = createViewInter(viewDirectory);
                PsiDirectory presenterDirectory = createMvpDirectory(LOW_PRESENTER, originalDirectory);
                PsiClass presenterInter = createPresenterInter(presenterDirectory);
                PsiDirectory modelDirectory = createMvpDirectory(LOW_MODEL, originalDirectory);
                PsiClass modelInter = createModelInter(modelDirectory);
                //创建impl
                String modelPackage = importClass(modelInter);
                String presenterPackage = importClass(presenterInter);
                String viewPackage = importClass(viewInter);
                PsiClass modelImpl = createModelImpl(modelDirectory, modelPackage);
                String modelImplPackage = importClass(modelImpl);
                PsiClass presenterImpl = createPresenterImpl(presenterDirectory, modelImplPackage, modelPackage, presenterPackage, viewPackage);
                //移动当前文件
                moveTargetFile(viewDirectory, viewInter, presenterInter, presenterImpl);
            }
        });
    }

    /**
     * 处理并移动当前文件
     *
     * @param viewDirectory
     * @param viewInter
     * @param presenterInter
     * @param presenterImpl
     */
    private void moveTargetFile(PsiDirectory viewDirectory, PsiClass viewInter, PsiClass presenterInter, PsiClass presenterImpl) {
        //获取activity/fragment文件夹
        PsiDirectory activityDirectory = viewDirectory.findSubdirectory(mIsActivity ? LOW_ACTIVITY : LOW_FRAGMENT);
        //获取activity/fragment包
        PsiPackage activityPackage = mDirectoryService.getPackage(activityDirectory);
        //修改包名
        PsiPackageStatement newStatement = mElementFactory.createPackageStatement(activityPackage.getQualifiedName());
        PsiPackageStatement currentStatement = mActivityFile.getPackageStatement();
        if (currentStatement != null) {
            mActivityFile.addBefore(newStatement, currentStatement);
            currentStatement.delete();
        }

        //移动文件到指定包下
        MoveFilesOrDirectoriesUtil.doMoveFile(mActivityFile, activityDirectory);

        //实现view接口
        boolean hasImplements = false;
        PsiReferenceList implementsList = mTargetClass.getImplementsList();
        if (implementsList != null) {
            PsiJavaCodeReferenceElement[] implementsElements = implementsList.getReferenceElements();
            if (implementsElements.length > 0) {
                hasImplements = true;
                PsiJavaCodeReferenceElement viewInterElement = mElementFactory.createClassReferenceElement(viewInter);
                boolean hasTargetInter = false;
                for (PsiJavaCodeReferenceElement implementsElement : implementsElements) {
                    if (implementsElement.getQualifiedName().equals(viewInterElement.getQualifiedName())) {
                        hasTargetInter = true;
                        break;
                    }
                }
                if (!hasTargetInter) {
                    implementsList.add(viewInterElement);
                }
            }

            if (!hasImplements) {
                implementsList.add(mElementFactory.createClassReferenceElement(viewInter));
            }

            implMethods(viewInter);
        }

        //创建presenter变量
        PsiField[] fields = mTargetClass.getFields();
        PsiJavaCodeReferenceElement presenterElement = mElementFactory.createClassReferenceElement(presenterInter);
        mHasPresenterInter = false;
        mPresenterInterField = null;
        for (PsiField field : fields) {
            if (mHasPresenterInter) {
                break;
            }
            mPresenterInterField = field;
            field.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
                    super.visitReferenceElement(reference);
                    if (reference.getQualifiedName().equals(presenterElement.getQualifiedName())) {
                        mHasPresenterInter = true;
                    }
                }
            });
        }
        if (!mHasPresenterInter) {
            PsiClassType type = mElementFactory.createType(presenterInter);
            mPresenterInterField = mElementFactory.createField("m" + presenterInter.getName(), type);
            mTargetClass.add(mPresenterInterField);
        }

        //变量赋值
        PsiMethod onCreateMethod = getTargetClassOnCreate();
        if (onCreateMethod == null) {
            onCreateMethod = findOnCreateMethod(mTargetClass);
        }
        if (onCreateMethod != null) {
            //添加onCreate方法
            PsiField[] psiFields = mTargetClass.getFields();
            List<PsiMethod> psiMethods = OverrideImplementUtil.overrideOrImplementMethod(mTargetClass, onCreateMethod, false);
            for (PsiMethod psiMethod : psiMethods) {
                mTargetClass.addAfter(psiMethod, psiFields[psiFields.length - 1]);
            }
            onCreateMethod = getTargetClassOnCreate();
            //创建赋值表达式
            PsiPackage presenterPackage = mDirectoryService.getPackage(presenterImpl.getContainingFile().getContainingDirectory());
            PsiExpression presenterExpression = mElementFactory.createExpressionFromText(mPresenterInterField.getName() + " = new "
                    + presenterPackage.getQualifiedName() + "." + presenterImpl.getName() + "(this)", onCreateMethod);
            //初始化标记
            mHasPresenterExpression = false;
            mBefPresenterElement = null;
            mSemicolon = null;
            mWhiteSpace = null;

            //查找"super.onCreate"
            onCreateMethod.accept(new JavaRecursiveElementWalkingVisitor() {

                @Override
                public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                    super.visitAssignmentExpression(expression);
                    if (presenterExpression.getText().contains(expression.getLExpression().getText()) && expression.getRExpression() != null) {
                        mHasPresenterExpression = true;
                    }
                }

                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                    super.visitMethodCallExpression(expression);
                    if ("super.onCreate".equals(expression.getMethodExpression().getQualifiedName())) {
                        mBefPresenterElement = expression;
                    }
                }
            });

            //添加赋值表达式
            createLineAndSemicolon();
            if (!mHasPresenterExpression && mBefPresenterElement != null) {
                mBefPresenterElement.add(mSemicolon);
                mBefPresenterElement.add(mWhiteSpace);
                mBefPresenterElement.add(presenterExpression);
                mStyleManager.optimizeImports(mTargetClass.getContainingFile());
                mStyleManager.shortenClassReferences(mTargetClass);
                //格式化代码
//                ReformatCodeAction
//                LastRunReformatCodeOptionsProvider provider = new LastRunReformatCodeOptionsProvider(PropertiesComponent.getInstance());
//                ReformatCodeRunOptions currentRunOptions = provider.getLastRunOptions(mActivityFile);
//                TextRangeType textRangeType = TextRangeType.WHOLE_FILE;
//                currentRunOptions.setProcessingScope(textRangeType);
//                (new FileInEditorProcessor(mActivityFile, mEditor, currentRunOptions)).processCode();
                CodeStyleManager.getInstance(mProject).reformatText(mActivityFile,0,mActivityFile.getTextLength());
            }
        }
    }

    /**
     * 创建“\n”和“;”
     */
    private void createLineAndSemicolon() {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "class _Test_ {\nint a = 10;}";
        PsiFile testFile = mPsiFileFactory.createFileFromText("_Test_.java", type, content);
        testFile.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitJavaToken(PsiJavaToken token) {
                super.visitJavaToken(token);
                if (mSemicolon == null && token.getTokenType().equals(JavaTokenType.SEMICOLON)) {
                    mSemicolon = token;
                }
            }

            @Override
            public void visitWhiteSpace(PsiWhiteSpace space) {
                super.visitWhiteSpace(space);
                if (mWhiteSpace == null && space.getText().equals("\n")) {
                    mWhiteSpace = space;
                }
            }
        });
    }

    /**
     * 找当前类的onCreate方法
     *
     * @return
     */
    private PsiMethod getTargetClassOnCreate() {
        PsiMethod[] onCreates = mTargetClass.findMethodsByName("onCreate", false);
        for (PsiMethod onCreate : onCreates) {
            if (onCreate.getParameterList().getParameters().length == 1) {
                return onCreate;
            }
        }
        return null;
    }

    /**
     * 寻找onCreate方法
     *
     * @param psiClass
     * @return
     */
    private PsiMethod findOnCreateMethod(PsiClass psiClass) {
        PsiClass superClass = psiClass.getSuperClass();
        if (superClass != null) {
            PsiMethod[] onCreates = superClass.findMethodsByName("onCreate", false);
            for (PsiMethod onCreate : onCreates) {
                if (onCreate.getParameterList().getParameters().length == 1) {
                    return onCreate;
                }
            }
            findOnCreateMethod(superClass);
        }
        return null;
    }

    /**
     * 复写inter方法
     *
     * @param viewInter
     */
    private void implMethods(PsiClass viewInter) {
        PsiMethod[] methods = viewInter.getMethods();
        for (PsiMethod method : methods) {
            List<PsiMethod> psiMethods = OverrideImplementUtil.overrideOrImplementMethod(mTargetClass, method, false);
            for (PsiMethod psiMethod : psiMethods) {
                mTargetClass.add(psiMethod);
            }
        }
    }

    /**
     * 获取包名
     *
     * @param psiClass
     */
    private String importClass(PsiClass psiClass) {
        PsiPackage directoryPackage = mDirectoryService.getPackage(psiClass.getContainingFile().getContainingDirectory());
        if (directoryPackage != null) {
            StringBuilder builder = new StringBuilder();
            builder.append("import ");
            builder.append(directoryPackage.getQualifiedName());
            builder.append(".");
            builder.append(psiClass.getName());
            builder.append(";");
            return builder.toString();
        }
        return null;
    }

    /**
     * 创建model的impl
     *
     * @param modelDirectory
     * @return
     */
    private PsiClass createModelImpl(PsiDirectory modelDirectory, String... packages) {
        PsiDirectory implDirectory = modelDirectory.findSubdirectory(LOW_IMPL);
        PsiFile implFile = implDirectory.findFile(getImplJavaFile(UP_MODEL));
        PsiClass implClass = null;
        if (implFile == null) {
            implClass = (PsiClass) implDirectory.add(createImplJava(UP_MODEL, packages));
        } else {
            implClass = mNamesCache.getClassesByName(getImplJavaClass(UP_MODEL), implDirectory.getResolveScope())[0];
        }
        return implClass;
    }

    /**
     * 创建presenter的impl
     *
     * @param presenterDirectory
     * @return
     */
    private PsiClass createPresenterImpl(PsiDirectory presenterDirectory, String... packages) {
        PsiDirectory implDirectory = presenterDirectory.findSubdirectory(LOW_IMPL);
        PsiFile implFile = implDirectory.findFile(getImplJavaFile(UP_PRESENTER));
        PsiClass implClass = null;
        if (implFile == null) {
            implClass = (PsiClass) implDirectory.add(createImplJava(UP_PRESENTER, packages));
        } else {
            implClass = mNamesCache.getClassesByName(getImplJavaClass(UP_PRESENTER), implDirectory.getResolveScope())[0];
        }
        return implClass;
    }

    /**
     * 创建impl的模板类
     *
     * @param mvp
     * @return
     */
    private PsiClass createImplJava(String mvp, String... packages) {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "";
        if (packages != null) {
            StringBuilder builder = new StringBuilder();
            for (String aPackage : packages) {
                builder.append(aPackage);
            }
            content = builder.toString();
        }
        switch (mvp) {
            case UP_PRESENTER:
                String interView = getInterJavaClass(UP_VIEW);
                String modelView = getInterJavaClass(UP_MODEL);
                String mInterView = " m" + interView;
                String aInterView = (mIsActivity ? " a" : " f") + interView;
                String mModelView = " m" + modelView;
                content += "public class " + getImplJavaClass(mvp) + " implements " + getInterJavaClass(mvp) + "{ private " + interView + mInterView
                        + ";private " + modelView + mModelView + ";public " + getImplJavaClass(mvp) + "(" + interView + aInterView + ") {" +
                        mInterView + " =" + aInterView + ";" + mModelView + " = new " + getImplJavaClass(UP_MODEL) + "();}}";
                break;
            case UP_MODEL:
                content += "public class " + getImplJavaClass(mvp) + " implements " + getInterJavaClass(mvp) + "{}";
                break;
        }
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(getImplJavaFile(mvp), type, content)).getClasses()[0];
    }

    /**
     * 创建model的interface
     *
     * @param modelDirectory
     * @return
     */
    private PsiClass createModelInter(PsiDirectory modelDirectory) {
        PsiDirectory interDirectory = modelDirectory.findSubdirectory(INTER);
        PsiFile modelFile = interDirectory.findFile(getInterJavaFile(UP_MODEL));
        PsiClass modelInter = null;
        if (modelFile == null) {
//            modelInter = mDirectoryService.createInterface(interDirectory, getInterJavaClass(UP_MODEL));
            modelInter = (PsiClass) interDirectory.add(createPresenterOrModelInterJava(getInterJavaClass(UP_MODEL)));
        } else {
            modelInter = mNamesCache.getClassesByName(getInterJavaClass(UP_MODEL), interDirectory.getResolveScope())[0];
        }
        return modelInter;
    }

    /**
     * 创建presenter的interface
     *
     * @param presenterDirectory
     * @return
     */
    private PsiClass createPresenterInter(PsiDirectory presenterDirectory) {
        PsiDirectory callbackDirectory = presenterDirectory.findSubdirectory(LOW_CALL_BACK);
        PsiFile callbackFile = callbackDirectory.findFile(UP_CALL_BACK + ".java");
        if (callbackFile == null) {
            callbackDirectory.add(createCallBack());
        }
        PsiDirectory interDirectory = presenterDirectory.findSubdirectory(INTER);
        PsiFile presenterFile = interDirectory.findFile(getInterJavaFile(UP_PRESENTER));
        PsiClass presenterInter = null;
        if (presenterFile == null) {
//            presenterInter = mDirectoryService.createInterface(interDirectory, getInterJavaClass(UP_PRESENTER));
            presenterInter = (PsiClass) interDirectory.add(createPresenterOrModelInterJava(getInterJavaClass(UP_PRESENTER)));
        } else {
            presenterInter = mNamesCache.getClassesByName(getInterJavaClass(UP_PRESENTER), interDirectory.getResolveScope())[0];
        }
        return presenterInter;
    }

    /**
     * 创建callback
     *
     * @return
     */
    private PsiClass createCallBack() {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "public interface CallBack<T> {  void onSuccess(T response);  void onError(Throwable t); }";
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(UP_CALL_BACK + ".java", type, content)).getClasses()[0];
    }

    /**
     * 创建view的interface
     *
     * @param viewDirectory
     */
    private PsiClass createViewInter(PsiDirectory viewDirectory) {
        PsiDirectory interDirectory = viewDirectory.findSubdirectory(INTER);
        PsiFile viewFile = interDirectory.findFile(getInterJavaFile(UP_VIEW));
        PsiClass interJava = createViewInterJava(getInterJavaClass(UP_VIEW));
        PsiClass viewInter = null;
        if (viewFile == null) {
            viewInter = (PsiClass) interDirectory.add(interJava);
        } else {
            viewInter = mNamesCache.getClassesByName(getInterJavaClass(UP_VIEW), interDirectory.getResolveScope())[0];
        }
        return viewInter;
    }

    /**
     * 创建View的interface模板类
     *
     * @param name
     * @return
     */
    private PsiClass createViewInterJava(String name) {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "public interface " + name + " { \n" +
                "//请求标记\n int REQUEST_ONE = 0; int REQUEST_TWO = 1; int REQUEST_THREE = 2; \n//响应标记\n int RESPONSE_ONE = 0; int RESPONSE_TWO = 1; "
                + "int RESPONSE_THREE = 2; <T> T request(int requestFlag); <T> void response(T response,int responseFlag);}";
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(name + ".java", type, content)).getClasses()[0];
    }

    /**
     * 创建presenter/model的interface模板类
     *
     * @param name
     * @return
     */
    private PsiClass createPresenterOrModelInterJava(String name) {
        JavaFileType type = JavaFileType.INSTANCE;
        String content = "public interface " + name + " {}";
        return ((PsiJavaFile) mPsiFileFactory.createFileFromText(name + ".java", type, content)).getClasses()[0];
    }

    /**
     * 获取根包
     *
     * @param currentDirectory
     * @return
     */
    private PsiDirectory getOriginalDirectory(PsiDirectory currentDirectory) {
        PsiPackage currentPackage = mDirectoryService.getPackage(currentDirectory);
        if (currentPackage != null) {
            if (currentPackage.getQualifiedName().contains("." + LOW_VIEW)) {
                return getOriginalDirectory(currentDirectory.getParent());
            }
        }
        return currentDirectory;
    }

    /**
     * 创建MVP包
     *
     * @param mvp
     * @param originalDirectory
     * @return
     */
    private PsiDirectory createMvpDirectory(String mvp, PsiDirectory originalDirectory) {
        PsiDirectory mvpDirectory = originalDirectory.findSubdirectory(mvp);
        if (mvpDirectory == null) {
            mvpDirectory = originalDirectory.createSubdirectory(mvp);
        }
        if (LOW_VIEW.equals(mvp)) {
            PsiDirectory activityDirectory = mvpDirectory.findSubdirectory(LOW_ACTIVITY);
            if (activityDirectory == null) {
                activityDirectory = mvpDirectory.createSubdirectory(LOW_ACTIVITY);
            }
            PsiDirectory fragmentDirectory = mvpDirectory.findSubdirectory(LOW_FRAGMENT);
            if (fragmentDirectory == null) {
                fragmentDirectory = mvpDirectory.createSubdirectory(LOW_FRAGMENT);
            }
        } else {
            PsiDirectory implDirectory = mvpDirectory.findSubdirectory(LOW_IMPL);
            if (implDirectory == null) {
                implDirectory = mvpDirectory.createSubdirectory(LOW_IMPL);
            }
            if (LOW_PRESENTER.equals(mvp)) {
                PsiDirectory callbackDirectory = mvpDirectory.findSubdirectory(LOW_CALL_BACK);
                if (callbackDirectory == null) {
                    implDirectory = mvpDirectory.createSubdirectory(LOW_CALL_BACK);
                }
            }
        }
        PsiDirectory interDirectory = mvpDirectory.findSubdirectory(INTER);
        if (interDirectory == null) {
            interDirectory = mvpDirectory.createSubdirectory(INTER);
        }
        return mvpDirectory;
    }


    /**
     * 获取带有后缀名Java的inter文件
     *
     * @param mvp
     * @return
     */
    private String getInterJavaFile(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append("I");
        builder.append(mName);
        builder.append(mvp);
        builder.append(".java");
        return builder.toString();
    }

    /**
     * 获取不带后缀名Java的inter类
     *
     * @param mvp
     * @return
     */
    private String getInterJavaClass(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append("I");
        builder.append(mName);
        builder.append(mvp);
        return builder.toString();
    }

    /**
     * 获取带有后缀名Java的class文件
     *
     * @param mvp
     * @return
     */
    private String getImplJavaFile(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append(mName);
        builder.append(mvp);
        builder.append(UP_IMPL);
        builder.append(".java");
        return builder.toString();
    }

    /**
     * 获取不带后缀名Java的class类
     *
     * @param mvp
     * @return
     */
    private String getImplJavaClass(String mvp) {
        StringBuilder builder = new StringBuilder();
        builder.append(mName);
        builder.append(mvp);
        builder.append(UP_IMPL);
        return builder.toString();
    }

}
