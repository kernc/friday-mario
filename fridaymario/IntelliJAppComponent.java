package fridaymario;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManagerListener;
import fridaymario.listeners.*;
import fridaymario.sounds.SilentSound;
import fridaymario.sounds.Sounds;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

public class IntelliJAppComponent implements ApplicationComponent {
	private final Map<Project, Refactoring> refactoringByProject = new HashMap<Project, Refactoring>();
	private final Map<Project, VcsActions> vcsActionsByProject = new HashMap<Project, VcsActions>();
	private final Map<Project, Compilation> compilationByProject = new HashMap<Project, Compilation>();
	private final Map<Project, UnitTests> unitTestsByProject = new HashMap<Project, UnitTests>();

	private ActionListeningSoundPlayer soundPlayer;
	private AllActions allActions;

	private ProjectManagerListener projectManagerListener;
	private ApplicationAdapter applicationListener;
	private boolean silentMode;
	private boolean logUnmappedActions;


	@Override public void initComponent() {
		if (!Settings.getInstance().isPluginEnabled()) return;
		init();
	}

	@Override public void disposeComponent() {
		if (!Settings.getInstance().isPluginEnabled()) return;
		dispose();
	}

	public void init() {
		soundPlayer = new ActionListeningSoundPlayer(createSounds(), createLoggingListener()).init();
		initApplicationListeners();
		initProjectListeners();
		Settings.getInstance().setPluginEnabled(true);
	}

	public void dispose() {
		disposeProjectListeners();
		disposeApplicationListeners();
		soundPlayer.stop();
		Settings.getInstance().setPluginEnabled(false);
	}

	private void initApplicationListeners() {
		allActions = new AllActions(soundPlayer);
		allActions.start();

		applicationListener = new ApplicationAdapter() {
			@Override public void applicationExiting() {
				soundPlayer.stopAndWait();
			}
		};
		ApplicationManager.getApplication().addApplicationListener(applicationListener);
	}

	private void disposeApplicationListeners() {
		ApplicationManager.getApplication().removeApplicationListener(applicationListener);
		allActions.stop();
	}

	private void initProjectListeners() {
		projectManagerListener = new ProjectManagerAdapter() {
			@Override public void projectOpened(Project project) {
				if (!refactoringByProject.containsKey(project)) {
					Refactoring refactoring = new Refactoring(project, soundPlayer);
					refactoring.start();
					refactoringByProject.put(project, refactoring);
				}

				if (!vcsActionsByProject.containsKey(project)) {
					VcsActions vcsActions = new VcsActions(project, soundPlayer);
					vcsActions.start();
					vcsActionsByProject.put(project, vcsActions);
				}

				if (!compilationByProject.containsKey(project) && isIdeWithCompilation()) {
					Compilation compilation = new Compilation(project, soundPlayer);
					compilation.start();
					compilationByProject.put(project, compilation);
				}

				if (!unitTestsByProject.containsKey(project)) {
					UnitTests unitTests = new UnitTests(project, soundPlayer);
					unitTests.start();
					unitTestsByProject.put(project, unitTests);
				}
			}

			@Override public void projectClosed(Project project) {
				if (refactoringByProject.containsKey(project)) {
					refactoringByProject.get(project).stop();
					refactoringByProject.remove(project);
				}
				if (vcsActionsByProject.containsKey(project)) {
					vcsActionsByProject.get(project).stop();
					vcsActionsByProject.remove(project);
				}
				if (compilationByProject.containsKey(project)) {
					compilationByProject.get(project).stop();
					compilationByProject.remove(project);
				}
				if (unitTestsByProject.containsKey(project)) {
					unitTestsByProject.get(project).stop();
					unitTestsByProject.remove(project);
				}
			}
		};

		for (Project project : ProjectManager.getInstance().getOpenProjects()) {
			projectManagerListener.projectOpened(project);
		}
		ProjectManager.getInstance().addProjectManagerListener(projectManagerListener);
	}

	private void disposeProjectListeners() {
		for (Project project : ProjectManager.getInstance().getOpenProjects()) {
			projectManagerListener.projectClosed(project);
		}
	}

	@SuppressWarnings("ConstantConditions")
	@NotNull @Override public String getComponentName() {
		return this.getClass().getCanonicalName();
	}

	public IntelliJAppComponent silentMode() {
		silentMode = true;
		return this;
	}

	public IntelliJAppComponent logUnmappedActionsMode() {
		logUnmappedActions = true;
		return this;
	}

	private Sounds createSounds() {
		if (silentMode) {
			return Sounds.createSilent(new SilentSound.Listener() {
				@Override public void playing(String soundName) {
					show(soundName);
				}

				@Override public void stopped(String soundName) {
					show("stopped: " + soundName);
				}
			});
		} else {
			return Sounds.create();
		}
	}

	private ActionListeningSoundPlayer.Listener createLoggingListener() {
		return new ActionListeningSoundPlayer.Listener() {
			@Override public void unmappedAction(String actionId) {
				if (logUnmappedActions) show(actionId);
			}

			@Override public void unmappedRefactoring(String refactoringId) {
				if (logUnmappedActions) show(refactoringId);
			}
		};
	}

	private static void show(String message) {
		if (isEmptyOrSpaces(message)) return;
		String noTitle = "";
		Notification notification = new Notification("Friday Mario", noTitle, message, NotificationType.INFORMATION);
		ApplicationManager.getApplication().getMessageBus().syncPublisher(Notifications.TOPIC).notify(notification);
	}

	private static IntelliJAppComponent instance() {
		return ApplicationManager.getApplication().getComponent(IntelliJAppComponent.class);
	}

	private static boolean isIdeWithCompilation() {
		try {
			Class.forName("com.intellij.openapi.compiler.CompilationStatusAdapter");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}


	@SuppressWarnings("ComponentNotRegistered") // inspection is wrong
	public static class StartStop extends AnAction {
		@Override public void actionPerformed(@NotNull AnActionEvent event) {
			if (Settings.getInstance().isPluginEnabled()) {
				instance().dispose();
			} else {
				instance().init();
			}
		}

		@Override public void update(@NotNull AnActionEvent event) {
			if (Settings.getInstance().isPluginEnabled()) {
				event.getPresentation().setText("Stop Friday Mario");
			} else {
				event.getPresentation().setText("Start Friday Mario");
			}
		}
	}

}
