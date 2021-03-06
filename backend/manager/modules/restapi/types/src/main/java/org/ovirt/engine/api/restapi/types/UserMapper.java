package org.ovirt.engine.api.restapi.types;

import org.apache.commons.lang.StringUtils;
import org.ovirt.engine.api.model.Domain;
import org.ovirt.engine.api.model.Group;
import org.ovirt.engine.api.model.Groups;
import org.ovirt.engine.api.model.User;
import org.ovirt.engine.core.common.businessentities.DbUser;
import org.ovirt.engine.core.common.businessentities.LdapUser;
import org.ovirt.engine.core.compat.Guid;

public class UserMapper {

    @Mapping(from = DbUser.class, to = User.class)
    public static User map(DbUser entity, User template) {
        User model = template != null ? template : new User();
        model.setName(entity.getFirstName());
        model.setUserName(entity.getLoginName());
        model.setId(entity.getId().toString());
        model.setLastName(entity.getLastName());
        model.setEmail(entity.getEmail());
        model.setDepartment(entity.getDepartment());
        if (entity.getGroupNames() != null && entity.getGroupNames().trim().length() > 0) {
            model.setGroups(new Groups());
            for (String name : entity.getGroupNames().split(",")) {
                Group group = new Group();
                group.setName(name);
                model.getGroups().getGroups().add(group);
            }
        }
        if (!StringUtils.isEmpty(entity.getDomain())) {
            Domain dom = new Domain();
            dom.setId(new Guid(entity.getDomain().getBytes(), true).toString());
            model.setDomain(dom);
        }
        return model;
    }

    @Mapping(from = LdapUser.class, to = User.class)
    public static User map(LdapUser entity, User template) {
        User model = template != null ? template : new User();
        model.setName(entity.getName());
        model.setUserName(entity.getUserName());
        model.setId(entity.getUserId().toString());
        model.setLastName(entity.getSurName());
        model.setEmail(entity.getEmail());
        model.setDepartment(entity.getDepartment());
        if (entity.getGroups() != null) {
            model.setGroups(new Groups());
            for (String name : entity.getGroups().keySet()) {
                Group group = new Group();
                group.setName(name);
                model.getGroups().getGroups().add(group);
            }
        }
        if (!StringUtils.isEmpty(entity.getDomainControler())) {
            Domain dom = new Domain();
            dom.setId(new Guid(entity.getDomainControler().getBytes(), true).toString());
            model.setDomain(dom);
        }
        return model;
    }

}
