#
# ovirt-engine-setup -- ovirt engine setup
# Copyright (C) 2013 Red Hat, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


"""CA plugin."""


import os
import re
import random
import gettext
_ = lambda m: gettext.dgettext(message=m, domain='ovirt-engine-setup')


from M2Crypto import X509


from otopi import util
from otopi import plugin
from otopi import transaction
from otopi import filetransaction
from otopi import constants as otopicons


from ovirt_engine_setup import constants as osetupcons
from ovirt_engine_setup import util as osetuputil


@util.export
class Plugin(plugin.PluginBase):
    """CA plugin."""

    class CATransaction(transaction.TransactionElement):
        """yum transaction element."""

        def __init__(self, parent, uninstall_files):
            self._parent = parent
            self._uninstall_files = uninstall_files

        def __str__(self):
            return _("CA Transaction")

        def prepare(self):
            pass

        def abort(self):
            for f in self._uninstall_files:
                if os.path.exists(f):
                    os.unlink(f)

        def commit(self):
            pass

    def _subjectComponentEscape(self, s):
        return osetuputil.escape(s, '/\\')

    def __init__(self, context):
        super(Plugin, self).__init__(context=context)
        self._enabled = False

    @plugin.event(
        stage=plugin.Stages.STAGE_INIT,
    )
    def _init(self):
        self.environment.setdefault(
            osetupcons.PKIEnv.STORE_PASS,
            osetupcons.Defaults.DEFAULT_PKI_STORE_PASS
        )
        self.environment.setdefault(
            osetupcons.PKIEnv.COUNTRY,
            osetupcons.Defaults.DEFAULT_PKI_COUNTRY
        )
        self.environment.setdefault(
            osetupcons.PKIEnv.ORG,
            None
        )

        self.environment[otopicons.CoreEnv.LOG_FILTER].append(
            self.environment[
                osetupcons.PKIEnv.STORE_PASS
            ]
        )

    @plugin.event(
        stage=plugin.Stages.STAGE_SETUP,
        condition=lambda self: not os.path.exists(
            osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_CA_CERT
        )
    )
    def _setup(self):
        self._enabled = True

    @plugin.event(
        stage=plugin.Stages.STAGE_CUSTOMIZATION,
        before=(
            osetupcons.Stages.DIALOG_TITLES_E_PKI,
        ),
        after=(
            osetupcons.Stages.CONFIG_PROTOCOLS_CUSTOMIZATION,
            osetupcons.Stages.DIALOG_TITLES_S_PKI,
        ),
    )
    def _customization(self):
        if self._enabled:
            if self.environment[osetupcons.PKIEnv.ORG] is None:
                org = 'Test'
                if '.' in self.environment[osetupcons.ConfigEnv.FQDN]:
                    org = self.environment[
                        osetupcons.ConfigEnv.FQDN
                    ].split('.', 1)[1]

                self.environment[
                    osetupcons.PKIEnv.ORG
                ] = self.dialog.queryString(
                    name='OVESETUP_PKI_ORG',
                    note=_('Organization name for certificate [@DEFAULT@]: '),
                    prompt=True,
                    default=org,
                )
        else:
            self.dialog.note(
                text=_('PKI is already configured'),
            )

    @plugin.event(
        stage=plugin.Stages.STAGE_MISC,
        name=osetupcons.Stages.CA_AVAILABLE,
        condition=lambda self: self._enabled,
    )
    def _misc(self):
        # TODO
        # this implementaiton is not transactional
        # too many issues with legacy ca implementation
        # need to work this out to allow transactional
        # for now just delete files if we fail
        uninstall_files = []
        self.environment[otopicons.CoreEnv.MAIN_TRANSACTION].append(
            self.CATransaction(
                parent=self,
                uninstall_files=uninstall_files,
            )
        )

        # LEGACY NOTE
        # This is needed for avoiding error in create_ca when supporting
        # max cn length of 64.
        # please DON'T increase this size, any value over 55 will fail the
        # setup. the truncated host-fqdn is concatenated with a random string
        # to create a unique CN value.
        self.environment[
            osetupcons.CoreEnv.REGISTER_UNINSTALL_GROUPS
        ].createGroup(
            group='ca_pki',
            description='PKI keys',
            optional=True,
        ).addFiles(
            group='ca_pki',
            fileList=uninstall_files,
        )
        MAX_HOST_FQDN_LEN = 55

        self.logger.info(_('Creating CA'))

        localtransaction = transaction.Transaction()
        with localtransaction:
            for name in (
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_CA_TEMPLATE,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_CERT_TEMPLATE,
            ):
                localtransaction.append(
                    filetransaction.FileTransaction(
                        name=name[:-len('.in')],
                        content=osetuputil.processTemplate(
                            name,
                            {
                                '@AIA@': 'http://%s:%s%s' % (
                                    self.environment[
                                        osetupcons.ConfigEnv.FQDN
                                    ],
                                    self.environment[
                                        osetupcons.ConfigEnv.PUBLIC_HTTP_PORT
                                    ],
                                    osetupcons.Const.ENGINE_PKI_CA_URI,
                                )
                            }
                        ),
                        modifiedList=uninstall_files,
                    ),
                )

        self.execute(
            args=(
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_CA_CREATE,
                '--subject=/C=%s/O=%s/CN=%s.%s' % (
                    self._subjectComponentEscape(
                        self.environment[osetupcons.PKIEnv.COUNTRY],
                    ),
                    self._subjectComponentEscape(
                        self.environment[osetupcons.PKIEnv.ORG],
                    ),
                    self._subjectComponentEscape(
                        self.environment[
                            osetupcons.ConfigEnv.FQDN
                        ][:MAX_HOST_FQDN_LEN],
                    ),
                    random.randint(10000, 99999),
                ),
                '--keystore-password=%s' % (
                    self.environment[osetupcons.PKIEnv.STORE_PASS],
                ),
            ),
            envAppend={
                'JAVA_HOME': self.environment[
                    osetupcons.ConfigEnv.JAVA_HOME
                ],
            },
        )

        for name in ('engine', 'apache', 'jboss'):
            self.execute(
                (
                    osetupcons.FileLocations.OVIRT_ENGINE_PKI_CA_ENROLL,
                    '--name=%s' % name,
                    '--password=%s' % (
                        self.environment[osetupcons.PKIEnv.STORE_PASS],
                    ),
                    '--subject=/C=%s/O=%s/CN=%s' % (
                        self._subjectComponentEscape(
                            self.environment[osetupcons.PKIEnv.COUNTRY],
                        ),
                        self._subjectComponentEscape(
                            self.environment[osetupcons.PKIEnv.ORG],
                        ),
                        self._subjectComponentEscape(
                            self.environment[osetupcons.ConfigEnv.FQDN],
                        ),
                    ),
                ),
            )

        uninstall_files.extend(
            (
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_CERT,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_STORE,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_CA_CERT,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_CA_KEY,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_CERT,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_STORE,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_TRUST_STORE,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_JBOSS_STORE,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_CA_CERT_CONF,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_CERT_CONF,
            )
        )

        self.execute(
            args=(
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_PKCS12_EXTRACT,
                '--name=apache',
                '--passin=%s' % (
                    self.environment[osetupcons.PKIEnv.STORE_PASS],
                ),
                '--key=%s' % (
                    osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_KEY,
                ),
            ),
            logStreams=False,
        )
        uninstall_files.append(
            osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_KEY
        )

        if not os.path.exists(
            osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_CA_CERT
        ):
            os.symlink(
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_CA_CERT,
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_CA_CERT
            )
            uninstall_files.append(
                osetupcons.FileLocations.OVIRT_ENGINE_PKI_APACHE_CA_CERT
            )

        for f in (
            osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_STORE,
            osetupcons.FileLocations.OVIRT_ENGINE_PKI_JBOSS_STORE,
        ):
            os.chown(
                f,
                osetuputil.getUid(
                    self.environment[osetupcons.SystemEnv.USER_ENGINE]
                ),
                -1,
            )

    @plugin.event(
        stage=plugin.Stages.STAGE_MISC,
        after=(
            osetupcons.Stages.DB_CONNECTION_AVAILABLE,
        ),
        condition=lambda self: self._enabled,
    )
    def miscOptions(self):
        self.environment[osetupcons.DBEnv.STATEMENT].updateVdcOptions(
            options=(
                {
                    'name': 'OrganizationName',
                    'value': self.environment[
                        osetupcons.PKIEnv.ORG
                    ],
                },
            ),
        )

    @plugin.event(
        stage=plugin.Stages.STAGE_CLOSEUP,
        before=(
            osetupcons.Stages.DIALOG_TITLES_E_SUMMARY,
        ),
        after=(
            osetupcons.Stages.DIALOG_TITLES_S_SUMMARY,
        ),
    )
    def _closeup(self):
        x509 = X509.load_cert(
            file=osetupcons.FileLocations.OVIRT_ENGINE_PKI_ENGINE_CA_CERT,
            format=X509.FORMAT_PEM,
        )
        self.dialog.note(
            text=_('Internal CA {fingerprint}').format(
                fingerprint=re.sub(
                    r'(..)',
                    r':\1',
                    x509.get_fingerprint(md='sha1'),
                )[1:],
            )
        )


# vim: expandtab tabstop=4 shiftwidth=4
