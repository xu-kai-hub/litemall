package org.linlinjava.litemall.admin.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.admin.vo.PermVo;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermissionUtil {

    public static List<PermVo> listPermVo(List<Permission> permissions) {
        List<PermVo> root = new ArrayList<>();
        for (Permission permission : permissions) {
            RequiresPermissions requiresPermissions = permission.getRequiresPermissions();
            RequiresPermissionsDesc requiresPermissionsDesc = permission.getRequiresPermissionsDesc();
            String[] menus = requiresPermissionsDesc.menu();
            if (menus.length != 2) {
                throw new RuntimeException("目前只支持两级菜单");
            }
            PermVo menu1 = findOrCreate(root, menus[0], menus[0]);
            PermVo menu2 = findOrCreate(menu1.getChildren(), menus[1], menus[1]);
            addButton(menu2, requiresPermissions, requiresPermissionsDesc, permission.getApi());
        }
        return root;
    }

    private static PermVo findOrCreate(List<PermVo> nodes, String label, String id) {
        for (PermVo node : nodes) {
            if (node.getLabel().equals(label)) {
                return node;
            }
        }
        PermVo node = new PermVo();
        node.setId(id);
        node.setLabel(label);
        node.setChildren(new ArrayList<>());
        nodes.add(node);
        return node;
    }

    private static void addButton(PermVo menu, RequiresPermissions requiresPermissions,
                                  RequiresPermissionsDesc desc, String api) {
        for (PermVo child : menu.getChildren()) {
            if (child.getLabel().equals(desc.button())) {
                throw new RuntimeException("权限已经存在，不能添加新权限");
            }
        }
        PermVo button = new PermVo();
        button.setId(requiresPermissions.value()[0]);
        button.setLabel(desc.button());
        button.setApi(api);
        menu.getChildren().add(button);
    }

    public static List<Permission> listPermission(ApplicationContext context, String basicPackage) {
        Map<String, Object> map = context.getBeansWithAnnotation(Controller.class);
        List<Permission> permissions = new ArrayList<>();
        for (Object bean : map.values()) {
            if (!StringUtils.contains(ClassUtils.getPackageName(bean.getClass()), basicPackage)) {
                continue;
            }
            permissions.addAll(permissionsOfController(bean.getClass().getSuperclass()));
        }
        return permissions;
    }

    private static List<Permission> permissionsOfController(Class<?> controllerClz) {
        List<Permission> permissions = new ArrayList<>();
        RequestMapping clazzRequestMapping = AnnotationUtils.findAnnotation(controllerClz, RequestMapping.class);
        String baseApi = clazzRequestMapping == null ? "" : clazzRequestMapping.value()[0];
        List<Method> methods = MethodUtils.getMethodsListWithAnnotation(controllerClz, RequiresPermissions.class);
        for (Method method : methods) {
            Permission permission = permissionOfMethod(method, baseApi);
            if (permission != null) {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    private static Permission permissionOfMethod(Method method, String baseApi) {
        RequiresPermissions requiresPermissions = AnnotationUtils.getAnnotation(method, RequiresPermissions.class);
        RequiresPermissionsDesc requiresPermissionsDesc = AnnotationUtils.getAnnotation(method, RequiresPermissionsDesc.class);
        if (requiresPermissions == null || requiresPermissionsDesc == null) {
            return null;
        }
        PostMapping postMapping = AnnotationUtils.getAnnotation(method, PostMapping.class);
        if (postMapping != null) {
            return buildPermission(requiresPermissions, requiresPermissionsDesc,
                    "POST " + baseApi + postMapping.value()[0]);
        }
        GetMapping getMapping = AnnotationUtils.getAnnotation(method, GetMapping.class);
        if (getMapping != null) {
            return buildPermission(requiresPermissions, requiresPermissionsDesc,
                    "GET " + baseApi + getMapping.value()[0]);
        }
        throw new RuntimeException("目前权限管理应该在method的前面使用GetMapping注解或者PostMapping注解");
    }

    private static Permission buildPermission(RequiresPermissions requiresPermissions,
                                              RequiresPermissionsDesc desc, String api) {
        Permission permission = new Permission();
        permission.setRequiresPermissions(requiresPermissions);
        permission.setRequiresPermissionsDesc(desc);
        permission.setApi(api);
        return permission;
    }

    public static Set<String> listPermissionString(List<Permission> permissions) {
        Set<String> permissionsString = new HashSet<>();
        for (Permission permission : permissions) {
            permissionsString.add(permission.getRequiresPermissions().value()[0]);
        }
        return permissionsString;
    }
}
