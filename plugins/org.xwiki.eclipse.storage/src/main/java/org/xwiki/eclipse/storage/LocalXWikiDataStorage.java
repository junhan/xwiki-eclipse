package org.xwiki.eclipse.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.xwiki.eclipse.model.XWikiEclipsePage;
import org.xwiki.eclipse.model.XWikiEclipsePageHistorySummary;
import org.xwiki.eclipse.model.XWikiEclipsePageSummary;
import org.xwiki.eclipse.model.XWikiEclipseSpaceSummary;
import org.xwiki.eclipse.model.XWikiEclipseWikiSummary;
import org.xwiki.eclipse.storage.utils.PageIdProcessor;
import org.xwiki.eclipse.storage.utils.StorageUtils;
import org.xwiki.xmlrpc.model.XWikiClass;
import org.xwiki.xmlrpc.model.XWikiClassSummary;
import org.xwiki.xmlrpc.model.XWikiObject;
import org.xwiki.xmlrpc.model.XWikiObjectSummary;
import org.xwiki.xmlrpc.model.XWikiPage;
import org.xwiki.xmlrpc.model.XWikiPageHistorySummary;
import org.xwiki.xmlrpc.model.XWikiPageSummary;

/**
 * This class implements a local data storage for XWiki elements that uses the Eclipse resource component. The local
 * storage is rooted at an IFolder passed to the constructor. The structure of the local storage is the following:
 * 
 * <pre>
 * Root 
 * + index
 *   + Space1 (directory having its name equals to the space key)
 *     + Page1 (directory having its name equals to the page id)
 *       |- Page1.xeps (containing the page summary)
 *       |- Object1.xeos (object summaries)
 *       |- ...
 *       |- ObjectN.xeos
 *     + Page2
 *       |- Page2.xeps
 *       |- ...
 *     + ...
 *   + Space2
 *     +...
 * + pages
 *   |- Page1.xep (the actual page information)
 *   |- ...
 * + objects
 *   |- Object1.xeo (the actual object information)
 *   |- ...
 * + classes
 *   |- Class1.xec (the actual class information)
 *   |- ...
 * </pre>
 * 
 * All xe* files contains an XML serialization of the corresponding XWiki Eclipse elements.
 * 
 * @version $Id$
 */
public class LocalXWikiDataStorage
{
    private static final String WIKI_SUMMARY_FILE_EXTENSION = "xews"; //$NON-NLS-1$

    private static final String SPACE_SUMMARY_FILE_EXTENSION = "xess"; //$NON-NLS-1$

    private static final String PAGE_SUMMARY_FILE_EXTENSION = "xeps"; //$NON-NLS-1$

    private static final String PAGE_FILE_EXTENSION = "xep"; //$NON-NLS-1$

    protected static final Object OBJECT_SUMMARY_FILE_EXTENSION = "xeos"; //$NON-NLS-1$

    protected static final Object OBJECT_FILE_EXTENSION = "xeo"; //$NON-NLS-1$

    protected static final Object CLASS_FILE_EXTENSION = "xec"; //$NON-NLS-1$

    private IPath INDEX_DIRECTORY = new Path("index"); //$NON-NLS-1$

    private IPath WIKIS_DIRECTORY = new Path("wikis"); //$NON-NLS-1$

    private IPath SPACES_DIRECTORY = new Path("spaces"); //$NON-NLS-1$

    private IPath PAGES_DIRECTORY = new Path("pages"); //$NON-NLS-1$

    private IPath OBJECTS_DIRECTORY = new Path("objects"); //$NON-NLS-1$

    private IPath CLASSES_DIRECTORY = new Path("classes"); //$NON-NLS-1$

    private IContainer baseFolder;

    public LocalXWikiDataStorage(IContainer baseFolder)
    {
        this.baseFolder = baseFolder;
    }

    public void dispose()
    {
        // Do nothing.
    }

    public XWikiEclipsePage getPage(String wiki, String space, String pageName, String language)
        throws XWikiEclipseStorageException
    {
        try {
            IFolder pageFolder = StorageUtils.createFolder(baseFolder.getFolder(PAGES_DIRECTORY));

            IFile pageFile = pageFolder.getFile(getFileNameForPage(wiki, space, pageName, language)); //$NON-NLS-1$
            if (pageFile.exists()) {
                XWikiEclipsePage result =
                    (XWikiEclipsePage) StorageUtils.readFromJSON(pageFile, XWikiEclipsePage.class.getCanonicalName());
                return result;
            }
        } catch (Exception e) {
            throw new XWikiEclipseStorageException(e);
        }

        return null;
    }

    private List<IResource> getChildResources(final IContainer parent, int depth) throws CoreException
    {
        final List<IResource> result = new ArrayList<IResource>();

        parent.accept(new IResourceVisitor()
        {
            public boolean visit(IResource resource) throws CoreException
            {
                if (!resource.equals(parent)) {
                    result.add(resource);
                }

                return true;
            }

        }, depth, IResource.NONE);

        return result;
    }

    public List<XWikiPageSummary> getPages(String spaceKey) throws XWikiEclipseStorageException
    {
        final List<XWikiPageSummary> result = new ArrayList<XWikiPageSummary>();

        IFolder spaceFolder = baseFolder.getFolder(INDEX_DIRECTORY).getFolder(spaceKey);
        if (spaceFolder.exists()) {
            try {
                List<IResource> spaceFolderResources = getChildResources(spaceFolder, IResource.DEPTH_ONE);
                for (IResource spaceFolderResource : spaceFolderResources) {
                    if (spaceFolderResource instanceof IFolder && spaceFolderResource.getName().indexOf('?') == -1) {
                        IFolder pageFolder = (IFolder) spaceFolderResource;
                        List<IResource> pageFolderResources = getChildResources(pageFolder, IResource.DEPTH_ONE);

                        boolean pageSummaryFound = false;
                        for (IResource pageFolderResource : pageFolderResources) {
                            if (pageFolderResource instanceof IFile) {
                                IFile file = (IFile) pageFolderResource;
                                if (file.getFileExtension().equals(PAGE_SUMMARY_FILE_EXTENSION)) {
                                    Map<String, Object> map = (Map<String, Object>) StorageUtils.readDataFromXML(file);
                                    XWikiPageSummary pageSummary = new XWikiPageSummary(map);
                                    result.add(pageSummary);
                                    pageSummaryFound = true;
                                }
                            }
                        }

                        /*
                         * This can happen, for example, if the user stores an object and has never stored the page it
                         * belongs to: we have a folder named after the page id containing the object summary, but no
                         * page summary is available in that folder. In this case we build a reduced summary with the
                         * information extracted from the folder name.
                         */
                        if (!pageSummaryFound) {
                            String[] pageIdComponents = pageFolder.getName().split("\\."); //$NON-NLS-1$
                            Assert.isTrue(pageIdComponents.length == 2);
                            XWikiPageSummary pageSummary = new XWikiPageSummary();
                            pageSummary.setId(pageFolder.getName());
                            pageSummary.setTitle(pageIdComponents[1]);
                            pageSummary.setSpace(pageIdComponents[0]);
                            result.add(pageSummary);
                        }
                    }
                }

            } catch (CoreException e) {
                throw new XWikiEclipseStorageException(e);
            }
        }

        return result;
    }

    public List<XWikiEclipseWikiSummary> getWikiSummaries() throws XWikiEclipseStorageException
    {
        final List<XWikiEclipseWikiSummary> result = new ArrayList<XWikiEclipseWikiSummary>();

        try {
            final IFolder wikiFolder = StorageUtils.createFolder(baseFolder.getFolder(WIKIS_DIRECTORY));

            List<IResource> wikiFolderResources = getChildResources(wikiFolder, IResource.DEPTH_ONE);
            for (IResource wikiFolderResource : wikiFolderResources) {
                if (wikiFolderResource instanceof IFile) {
                    IFile wikiFile = (IFile) wikiFolderResource;
                    XWikiEclipseWikiSummary wikiSummary;
                    try {
                        wikiSummary =
                            (XWikiEclipseWikiSummary) StorageUtils.readFromJSON(wikiFile,
                                XWikiEclipseWikiSummary.class.getCanonicalName());
                        result.add(wikiSummary);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return result;

    }

    public XWikiEclipseWikiSummary storeWiki(final XWikiEclipseWikiSummary wiki) throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    /* Write the wiki summary */
                    // XWikiEclipseWikiSummary wikiSummary = new XWikiEclipseWikiSummary(wiki.getDataManager());
                    // wikiSummary.setBaseUrl("local");
                    // wikiSummary.setName(wiki.getName());
                    // wikiSummary.setSyntaxes(wiki.getSyntaxes());
                    // wikiSummary.setVersion(wiki.getVersion());
                    // wikiSummary.setWikiId(wiki.getWikiId());

                    StorageUtils.writeToJson(
                        baseFolder.getFolder(WIKIS_DIRECTORY).getFile(getFileNameForWikiSummary(wiki.getWikiId())),
                        wiki);
                }
            }, null);
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return wiki;
    }

    public List<XWikiEclipseSpaceSummary> getSpaces(String wikiId) throws XWikiEclipseStorageException
    {

        final List<XWikiEclipseSpaceSummary> result = new ArrayList<XWikiEclipseSpaceSummary>();

        try {
            final IFolder spaceFolder = StorageUtils.createFolder(baseFolder.getFolder(SPACES_DIRECTORY));

            List<IResource> spaceFolderResources = getChildResources(spaceFolder, IResource.DEPTH_ONE);
            for (IResource spaceFolderResource : spaceFolderResources) {
                if (spaceFolderResource instanceof IFile) {
                    IFile wikiFile = (IFile) spaceFolderResource;
                    XWikiEclipseSpaceSummary spaceSummary;
                    try {
                        spaceSummary =
                            (XWikiEclipseSpaceSummary) StorageUtils.readFromJSON(wikiFile,
                                XWikiEclipseSpaceSummary.class.getCanonicalName());
                        if (spaceSummary.getWiki().equals(wikiId)) {
                            result.add(spaceSummary);
                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return result;
    }

    // public void removeSpace(String spaceKey) throws XWikiEclipseStorageException
    // {
    // // Delete pages
    // List<XWikiPageSummary> pages = getPages(spaceKey);
    // for (XWikiPageSummary page : pages) {
    // removePage(page.getId());
    // }
    //
    // // Delete space directory
    // try {
    // final IFolder indexFolder = StorageUtils.createFolder(baseFolder.getFolder(INDEX_DIRECTORY));
    //
    // List<IResource> indexFolderResources = getChildResources(indexFolder, IResource.DEPTH_ONE);
    // for (IResource indexFolderResource : indexFolderResources) {
    // if (indexFolderResource instanceof IFolder) {
    // IFolder folder = (IFolder) indexFolderResource;
    // if (folder.getName().equals(spaceKey)) {
    // folder.delete(true, null);
    // break;
    // }
    // }
    // }
    // } catch (CoreException e) {
    // throw new XWikiEclipseStorageException(e);
    // }
    // }

    public XWikiEclipsePage storePage(final XWikiEclipsePage page) throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    // XWikiEclipsePage p = new XWikiEclipsePage(page.getDataManager());
                    //
                    // p.setId(page.getId());
                    // p.setParentId(page.getParentId());
                    // p.setTitle(page.getTitle());
                    // p.setUrl(page.getUrl());
                    // p.setContent(page.getContent());
                    // p.setSpace(page.getSpace());
                    // p.setWiki(page.getWiki());
                    // p.setSpace(page.getSpace());
                    // p.setMajorVersion(page.getMajorVersion());
                    // p.setMinorVersion(page.getMinorVersion());
                    // p.setVersion(page.getVersion());
                    // p.setName(page.getName());
                    // p.setLanguage(page.getLanguage());

                    /* Write the page, considering the translation language */
                    String fileName =
                        getFileNameForPage(page.getWiki(), page.getSpace(), page.getName(), page.getLanguage());
                    StorageUtils.writeToJson(baseFolder.getFolder(PAGES_DIRECTORY).getFile(fileName), page);
                }
            }, null);
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return page;
    }

    /**
     * @param wiki
     * @param space
     * @return
     * @throws XWikiEclipseStorageException
     */
    public XWikiEclipseSpaceSummary getSpace(String wiki, String space) throws XWikiEclipseStorageException
    {
        List<XWikiEclipseSpaceSummary> spaces = getSpaces(wiki);
        for (XWikiEclipseSpaceSummary s : spaces) {
            if (s.getName().equals(space)) {
                return s;
            }
        }

        return null;
    }

    // public XWikiPage storePage(final XWikiPage page) throws XWikiEclipseStorageException
    // {
    // try {
    // ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
    // {
    // public void run(IProgressMonitor monitor) throws CoreException
    // {
    // /* Write the page summary */
    // XWikiPageSummary pageSummary = new XWikiPageSummary();
    // pageSummary.setId(page.getId());
    // pageSummary.setLocks(page.getLocks());
    // pageSummary.setParentId(page.getParentId());
    // pageSummary.setTitle(page.getTitle());
    // pageSummary.setTranslations(page.getTranslations() != null ? page.getTranslations()
    // : new ArrayList());
    // pageSummary.setUrl(page.getUrl());
    // // StorageUtils.writeDataToXML(baseFolder.getFolder(INDEX_DIRECTORY).getFolder(page.getSpace())
    //                    //                        .getFolder(page.getId()).getFile(getFileNameForPageSummary(pageSummary.getId())), //$NON-NLS-1$
    // // pageSummary.toRawMap());
    //
    // /* Write the page */
    // StorageUtils.writeDataToXML(
    //                        baseFolder.getFolder(PAGES_DIRECTORY).getFile(getFileNameForPage(page.getId())), page //$NON-NLS-1$
    // .toRawMap());
    // }
    // }, null);
    // } catch (CoreException e) {
    // throw new XWikiEclipseStorageException(e);
    // }
    //
    // return page;
    // }

    // public boolean removePage(final String pageId) throws XWikiEclipseStorageException
    // {
    // try {
    // ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
    // {
    // public void run(IProgressMonitor monitor) throws CoreException
    // {
    // try {
    // XWikiPage page = null;// getPage(pageId);
    // if (page == null) {
    // return;
    // }
    //
    // /* Remove page objects */
    // List<XWikiObjectSummary> objects = getObjects(pageId);
    // for (XWikiObjectSummary object : objects) {
    // removeObject(pageId, object.getClassName(), object.getId());
    // }
    //
    // /*
    // * Remove the index page folder with all the information (page and object summaries)
    // */
    // IFolder indexPageFolder =
    // baseFolder.getFolder(INDEX_DIRECTORY).getFolder(page.getSpace()).getFolder(pageId);
    // if (indexPageFolder.exists()) {
    // indexPageFolder.delete(true, null);
    // }
    //
    // /* Remove page */
    // IFolder pageFolder = StorageUtils.createFolder(baseFolder.getFolder(PAGES_DIRECTORY));
    //                        IFile pageFile = pageFolder.getFile(getFileNameForPage(pageId)); //$NON-NLS-1$
    // if (pageFile.exists()) {
    // pageFile.delete(true, null);
    // }
    // } catch (XWikiEclipseStorageException e) {
    // throw new CoreException(new Status(Status.ERROR, StoragePlugin.PLUGIN_ID, "Error", e));
    // }
    // }
    // }, null);
    //
    // return true;
    // } catch (CoreException e) {
    // throw new XWikiEclipseStorageException(e);
    // }
    // }

    public List<XWikiObjectSummary> getObjects(String pageId) throws XWikiEclipseStorageException
    {
        List<XWikiObjectSummary> result = new ArrayList<XWikiObjectSummary>();

        XWikiPage page = null;// getPage(pageId);
        if (page == null) {
            return result;
        }

        IFolder pageFolder = baseFolder.getFolder(INDEX_DIRECTORY).getFolder(page.getSpace()).getFolder(pageId);
        if (pageFolder.exists()) {
            try {
                List<IResource> pageFolderResources = getChildResources(pageFolder, IResource.DEPTH_ONE);
                for (IResource pageFolderResource : pageFolderResources) {
                    if (pageFolderResource instanceof IFile) {
                        IFile file = (IFile) pageFolderResource;
                        if (file.getFileExtension().equals(OBJECT_SUMMARY_FILE_EXTENSION)) {
                            Map<String, Object> map = (Map<String, Object>) StorageUtils.readDataFromXML(file);
                            XWikiObjectSummary objectSummary = new XWikiObjectSummary(map);
                            result.add(objectSummary);
                        }
                    }
                }
            } catch (CoreException e) {
                throw new XWikiEclipseStorageException(e);
            }
        }

        return result;
    }

    // FIXME: not yet implemented
    public XWikiObject getObject(String pageId, String className, int number) throws XWikiEclipseStorageException
    {
        try {
            IFolder objectsFolder = StorageUtils.createFolder(baseFolder.getFolder(OBJECTS_DIRECTORY));

            IFile objectFile = objectsFolder.getFile(getFileNameForObject(pageId, className, number)); //$NON-NLS-1$
            if (objectFile.exists()) {
                Map<String, Object> map = (Map<String, Object>) StorageUtils.readDataFromXML(objectFile);
                return new XWikiObject(map);
            }
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return null;
    }

    // FIXME: not yet implemented
    public XWikiClass getClass(String classId) throws XWikiEclipseStorageException
    {
        try {
            IFolder classesFolder = StorageUtils.createFolder(baseFolder.getFolder(CLASSES_DIRECTORY));

            IFile classFile = classesFolder.getFile(getFileNameForClass(classId)); //$NON-NLS-1$
            if (classFile.exists()) {
                Map<String, Object> map = (Map<String, Object>) StorageUtils.readDataFromXML(classFile);
                return new XWikiClass(map);
            }
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return null;
    }

    // FIXME: not yet implemented
    public XWikiObject storeObject(final XWikiObject object) throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    // try {
                    /* Write the object summary */
                    XWikiObjectSummary objectSummary = new XWikiObjectSummary();
                    objectSummary.setClassName(object.getClassName());
                    objectSummary.setId(object.getId());
                    objectSummary.setPageId(object.getPageId());
                    objectSummary.setPrettyName(object.getPrettyName());

                    XWikiPage page = null; // getPage(object.getPageId());
                    if (page == null) {
                        return;
                    }

                    StorageUtils.writeDataToXML(
                        baseFolder
                            .getFolder(INDEX_DIRECTORY)
                            .getFolder(page.getSpace())
                            .getFolder(object.getPageId())
                            .getFile(
                                getFileNameForObjectSummary(objectSummary.getPageId(), objectSummary.getClassName(),
                                    objectSummary.getId())), //$NON-NLS-1$
                        objectSummary.toRawMap());

                    /* Write the object */
                    StorageUtils.writeDataToXML(
                        baseFolder.getFolder(OBJECTS_DIRECTORY).getFile(
                            getFileNameForObject(object.getPageId(), object.getClassName(), object.getId())), object //$NON-NLS-1$
                            .toRawMap());
                    // } catch (XWikiEclipseStorageException e) {
                    // throw new CoreException(new Status(Status.ERROR, StoragePlugin.PLUGIN_ID, "Error", e));
                    // }
                }
            }, null);
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return object;
    }

    // FIXME: not yet implemented
    public void storeClass(final XWikiClass xwikiClass) throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    StorageUtils.writeDataToXML(
                        baseFolder.getFolder(CLASSES_DIRECTORY).getFile(getFileNameForClass(xwikiClass.getId())),
                        xwikiClass //$NON-NLS-1$
                            .toRawMap());
                }
            }, null);
        } catch (CoreException e) {
            new XWikiEclipseStorageException(e);
        }
    }

    /**
     * @param wiki
     * @return
     */
    public boolean wikiExists(String wiki)
    {
        IFile wikiFile = baseFolder.getFolder(WIKIS_DIRECTORY).getFile(getFileNameForWikiSummary(wiki));
        return wikiFile.exists();
    }

    public boolean spaceExists(String wiki, String space)
    {
        IFile spaceFile = baseFolder.getFolder(SPACES_DIRECTORY).getFile(getFileNameForSpaceSummary(wiki, space));
        return spaceFile.exists();
    }

    public boolean pageExists(String wiki, String space, String page, String language)
    {
        IFile pageFile = baseFolder.getFolder(PAGES_DIRECTORY).getFile(getFileNameForPage(wiki, space, page, language));
        return pageFile.exists();
    }

    public boolean objectExists(String pageId, String className, int number)
    {
        IFile objectFile =
            baseFolder.getFolder(OBJECTS_DIRECTORY).getFile(getFileNameForObject(pageId, className, number));
        return objectFile.exists();
    }

    private String getFileNameForSpaceSummary(String wiki, String space)
    {
        return String.format("%s.%s.%s", wiki, space, SPACE_SUMMARY_FILE_EXTENSION); //$NON-NLS-1$
    }

    private String getFileNameForWikiSummary(String wikiId)
    {
        return String.format("%s.%s", wikiId, WIKI_SUMMARY_FILE_EXTENSION); //$NON-NLS-1$
    }

    private String getFileNameForPageSummary(String wiki, String space, String page, String language)
    {
        if (language != null && !language.equals("")) {
            return String.format("%s.%s.%s.%s.%s", wiki, space, page, language, PAGE_SUMMARY_FILE_EXTENSION); //$NON-NLS-1$
        } else {
            return String.format("%s.%s.%s.%s", wiki, space, page, PAGE_SUMMARY_FILE_EXTENSION); //$NON-NLS-1$    
        }

    }

    private String getFileNameForPage(String wiki, String space, String page, String language)
    {
        if (language != null && !language.equals("")) {
            return String.format("%s.%s.%s.%s.%s", wiki, space, page, language, PAGE_FILE_EXTENSION); //$NON-NLS-1$
        } else {
            return String.format("%s.%s.%s.%s", wiki, space, page, PAGE_FILE_EXTENSION); //$NON-NLS-1$    
        }
    }

    private String getFileNameForObjectSummary(String pageId, String className, int id)
    {
        return String.format("%s.%s.%d.%s", pageId, className, id, OBJECT_SUMMARY_FILE_EXTENSION); //$NON-NLS-1$
    }

    private String getFileNameForObject(String pageId, String className, int number)
    {
        return String.format("%s.%s.%d.%s", pageId, className, number, OBJECT_FILE_EXTENSION); //$NON-NLS-1$
    }

    private String getFileNameForClass(String id)
    {
        return String.format("%s.%s", id, CLASS_FILE_EXTENSION); //$NON-NLS-1$
    }

    // FIXME: not yet implemented
    public List<XWikiClassSummary> getClasses() throws XWikiEclipseStorageException
    {
        List<XWikiClassSummary> result = new ArrayList<XWikiClassSummary>();

        try {
            List<IResource> classFolderResources =
                getChildResources(baseFolder.getFolder(CLASSES_DIRECTORY), IResource.DEPTH_ONE);
            for (IResource classFolderResource : classFolderResources) {
                if (classFolderResource instanceof IFile) {
                    IFile file = (IFile) classFolderResource;
                    if (file.getFileExtension().equals(CLASS_FILE_EXTENSION)) {
                        Map<String, Object> map = (Map<String, Object>) StorageUtils.readDataFromXML(file);
                        XWikiClass xwikiClass = new XWikiClass(map);
                        XWikiClassSummary classSummary = new XWikiClassSummary();
                        classSummary.setId(xwikiClass.getId());
                        result.add(classSummary);
                    }
                }
            }
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return result;
    }

    // FIXME: not yet implemented
    public boolean removeObject(final String pageId, final String className, final int number)
        throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    // try {
                    IFile file =
                        baseFolder.getFolder(OBJECTS_DIRECTORY)
                            .getFile(getFileNameForObject(pageId, className, number));
                    if (file.exists()) {
                        file.delete(true, null);
                    }

                    XWikiPage page = null; // getPage(pageId);
                    if (page == null) {
                        return;
                    }

                    file =
                        baseFolder.getFolder(INDEX_DIRECTORY).getFolder(page.getSpace()).getFolder(pageId)
                            .getFile(getFileNameForObjectSummary(pageId, className, number));
                    if (file.exists()) {
                        file.delete(true, null);
                    }
                    // } catch (XWikiEclipseStorageException e) {
                    // throw new CoreException(new Status(Status.ERROR, StoragePlugin.PLUGIN_ID, "Error", e));
                    // }
                }
            }, null);
        } catch (CoreException e) {
            new XWikiEclipseStorageException(e);
        }

        return true;
    }

    public XWikiPageSummary getPageSummary(String pageId) throws XWikiEclipseStorageException
    {
        XWikiPage page = null; // getPage(pageId);
        if (page == null) {
            return null;
        }

        List<XWikiPageSummary> pageSummaries = getPages(page.getSpace());

        for (XWikiPageSummary pageSummary : pageSummaries) {
            if (pageSummary.getId().equals(pageId)) {
                return pageSummary;
            }
        }

        return null;
    }

    public List<XWikiPageHistorySummary> getPageHistory(String pageId) throws XWikiEclipseStorageException
    {
        // Currently not supported in local storage.
        return new ArrayList<XWikiPageHistorySummary>();
    }

    public List<XWikiPageSummary> getAllPageIds() throws XWikiEclipseStorageException
    {
        List<XWikiPageSummary> result = new ArrayList<XWikiPageSummary>();

        // List<SpaceSummary> spaces = getSpaces();
        // for (SpaceSummary spaceSummary : spaces) {
        // List<XWikiPageSummary> pages = getPages(spaceSummary.getKey());
        // for (XWikiPageSummary pageSummary : pages) {
        // result.add(pageSummary);
        // }
        // }

        return result;
    }

    /**
     * @param spaceSummary
     */
    public XWikiEclipseSpaceSummary storeSpace(final XWikiEclipseSpaceSummary spaceSummary)
        throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    /* Write the space summary */
                    // XWikiEclipseSpaceSummary space = new XWikiEclipseSpaceSummary(spaceSummary.getDataManager());
                    // space.setUrl("local");
                    // space.setName(spaceSummary.getName());
                    // space.setWiki(spaceSummary.getWiki());
                    // space.setId(spaceSummary.getId());

                    String fileName = getFileNameForSpaceSummary(spaceSummary.getWiki(), spaceSummary.getName());
                    StorageUtils.writeToJson(baseFolder.getFolder(SPACES_DIRECTORY).getFile(fileName), spaceSummary);
                }
            }, null);
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return spaceSummary;

    }

    /**
     * @param pageSummary
     */
    public XWikiEclipsePageSummary storePageSummary(final XWikiEclipsePageSummary pageSummary)
        throws XWikiEclipseStorageException
    {
        try {
            ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
            {
                public void run(IProgressMonitor monitor) throws CoreException
                {
                    /* Write the page summary */
                    // XWikiEclipsePageSummary p = new XWikiEclipsePageSummary(pageSummary.getDataManager());
                    // p.setUrl(pageSummary.getUrl());
                    // p.setLanguage(pageSummary.getLanguage());
                    // p.setName(pageSummary.getName());
                    // p.setWiki(pageSummary.getWiki());
                    // p.setSpace(pageSummary.getSpace());
                    // p.setId(pageSummary.getId());
                    // p.setParentId(pageSummary.getParentId());
                    // p.setTitle(pageSummary.getTitle());
                    // p.setSyntax(pageSummary.getSyntax());

                    String fileName =
                        getFileNameForPageSummary(pageSummary.getWiki(), pageSummary.getSpace(), pageSummary.getName(),
                            pageSummary.getLanguage());

                    StorageUtils.writeToJson(baseFolder.getFolder(PAGES_DIRECTORY).getFile(fileName), pageSummary);
                }
            }, null);
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return pageSummary;

    }

    /**
     * @param spaceSummary
     * @return
     */
    public List<XWikiEclipsePageSummary> getPageSummaries(String wiki, String space)
        throws XWikiEclipseStorageException
    {
        final List<XWikiEclipsePageSummary> result = new ArrayList<XWikiEclipsePageSummary>();

        try {
            final IFolder spaceFolder = StorageUtils.createFolder(baseFolder.getFolder(PAGES_DIRECTORY));

            List<IResource> pageFolderResources = getChildResources(spaceFolder, IResource.DEPTH_ONE);
            for (IResource pageFolderResource : pageFolderResources) {
                if (pageFolderResource instanceof IFile) {
                    IFile wikiFile = (IFile) pageFolderResource;
                    XWikiEclipsePageSummary pageSummary;
                    try {
                        pageSummary =
                            (XWikiEclipsePageSummary) StorageUtils.readFromJSON(wikiFile,
                                XWikiEclipsePageSummary.class.getCanonicalName());
                        if (pageSummary.getWiki().equals(wiki) && pageSummary.getSpace().equals(space)) {
                            result.add(pageSummary);
                        }
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
        } catch (CoreException e) {
            throw new XWikiEclipseStorageException(e);
        }

        return result;
    }

    /**
     * @param pageSummary
     * @return
     */
    public List<XWikiEclipsePageHistorySummary> getPageHistorySummaries(String wiki, String space, String pageName,
        String language)
    {
        /* Currently not supported in local storage. */
        return new ArrayList<XWikiEclipsePageHistorySummary>();
    }

    /**
     * @param pageId
     * @throws CoreException
     */
    public void removePage(final String pageId) throws CoreException
    {

        ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable()
        {

            @Override
            public void run(IProgressMonitor monitor) throws CoreException
            {
                PageIdProcessor parser = new PageIdProcessor(pageId);
                IFolder pageFolder;
                pageFolder = StorageUtils.createFolder(baseFolder.getFolder(PAGES_DIRECTORY));

                IFile pageFile =
                    pageFolder.getFile(getFileNameForPage(parser.getWiki(), parser.getSpace(), parser.getPage(),
                        parser.getLanguage())); //$NON-NLS-1$
                if (pageFile.exists()) {
                    pageFile.delete(true, null);
                }

                IFile pageSummaryFile =
                    pageFolder.getFile(getFileNameForPageSummary(parser.getWiki(), parser.getSpace(), parser.getPage(),
                        parser.getLanguage())); //$NON-NLS-1$
                if (pageSummaryFile.exists()) {
                    pageSummaryFile.delete(true, null);
                }

                // FIXME: remove the objects of this page as well

            }
        }, null);

    }
}
