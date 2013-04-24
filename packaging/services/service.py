# Copyright 2012 Red Hat
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


import re
import glob
import logging
import logging.handlers
import optparse
import os
import shutil
import signal
import subprocess
import sys
import time
import gettext
_ = lambda m: gettext.dgettext(message=m, domain='ovirt-engine')


import daemon


def setupLogger():
    class _MyFormatter(logging.Formatter):
        """Needed as syslog will truncate any lines after first."""

        def __init__(
            self,
            fmt=None,
            datefmt=None,
        ):
            logging.Formatter.__init__(self, fmt=fmt, datefmt=datefmt)

        def format(self, record):
            return logging.Formatter.format(self, record).replace('\n', ' | ')

    logger = logging.getLogger('ovirt')
    logger.propagate = False
    if os.environ.get('OVIRT_ENGINE_SERVICE_DEBUG', '0') != '0':
        logger.setLevel(logging.DEBUG)
    else:
        logger.setLevel(logging.INFO)
    h = logging.handlers.SysLogHandler(
        address='/dev/log',
        facility=logging.handlers.SysLogHandler.LOG_DAEMON,
    )
    h.setLevel(logging.DEBUG)
    h.setFormatter(
        _MyFormatter(
            fmt=(
                os.path.splitext(os.path.basename(sys.argv[0]))[0] +
                '[%(process)s] '
                '%(levelname)s '
                '%(funcName)s:%(lineno)d '
                '%(message)s'
            ),
        ),
    )
    logger.addHandler(h)


class Base(object):
    """
    Base class for logging.
    """
    def __init__(self):
        self._logger = logging.getLogger(
            'ovirt.service.%s' % self.__class__.__name__
        )


class ConfigFile(Base):
    """
    Helper class to simplify getting values from the configuration, specially
    from the template used to generate the application server configuration
    file
    """

    # Compile regular expressions:
    COMMENT_EXPR = re.compile(r'\s*#.*$')
    BLANK_EXPR = re.compile(r'^\s*$')
    VALUE_EXPR = re.compile(r'^\s*(?P<key>\w+)\s*=\s*(?P<value>.*?)\s*$')
    REF_EXPR = re.compile(r'\$\{(?P<ref>\w+)\}')

    def __init__(self, files):
        super(ConfigFile, self).__init__()

        self._dir = dir
        # Save the list of files:
        self.files = files

        # Start with an empty set of values:
        self.values = {}

        # Merge all the given configuration files, in the same order
        # given, so that the values in one file are overriden by values
        # in files appearing later in the list:
        for file in self.files:
            self.loadFile(file)
            for filed in sorted(
                glob.glob(
                    os.path.join(
                        '%s.d' % file,
                        '*.conf',
                    )
                )
            ):
                self.loadFile(filed)

    def loadFile(self, file):
        if os.path.exists(file):
            self._logger.debug("loading config '%s'", file)
            with open(file, 'r') as f:
                for line in f:
                    self.loadLine(line)

    def loadLine(self, line):
        # Remove comments:
        commentMatch = self.COMMENT_EXPR.search(line)
        if commentMatch is not None:
            line = line[:commentMatch.start()] + line[commentMatch.end():]

        # Skip empty lines:
        emptyMatch = self.BLANK_EXPR.search(line)
        if emptyMatch is not None:
            return

        # Separate name from value:
        keyValueMatch = self.VALUE_EXPR.search(line)
        if keyValueMatch is None:
            return
        key = keyValueMatch.group('key')
        value = keyValueMatch.group('value')

        # Strip quotes from value:
        if len(value) >= 2 and value[0] == '"' and value[-1] == '"':
            value = value[1:-1]

        # Expand references to other parameters:
        while True:
            refMatch = self.REF_EXPR.search(value)
            if refMatch is None:
                break
            refKey = refMatch.group('ref')
            refValue = self.values.get(refKey)
            if refValue is None:
                break
            value = '%s%s%s' % (
                value[:refMatch.start()],
                refValue,
                value[refMatch.end():],
            )

        # Update the values:
        self.values[key] = value

    def getString(self, name):
        text = self.values.get(name)
        if text is None:
            raise RuntimeError(
                _("The parameter '{name}' does not have a value").format(
                    name=name,
                )
            )
        return text

    def getBoolean(self, name):
        return self.getString(name) in ('t', 'true', 'y', 'yes', '1')

    def getInteger(self, name):
        value = self.getString(name)
        try:
            return int(value)
        except ValueError:
            raise RuntimeError(
                _(
                    "The value '{value}' of parameter '{name}' "
                    "is not a valid integer"
                ).format(
                    name,
                    value,
                )
            )


class TempDir(Base):
    """
    Temporary directory scope management

    Usage:
        with TempDir(directory):
            pass
    """

    def _clear(self):
        self._logger.debug("removing directory '%s'", self._dir)
        if os.path.exists(self._dir):
            shutil.rmtree(self._dir)

    def __init__(self, dir):
        super(TempDir, self).__init__()
        self._dir = dir

    def create(self):
        self._clear()
        os.makedirs(self._dir)

    def destroy(self):
        try:
            self._clear()
        except Exception as e:
            self._logger.warning(
                _("Cannot remove directory '{directory}': {error}").format(
                    directory=self._dir,
                    error=e,
                ),
            )
            self._logger.debug('exception', exc_info=True)

    def __enter__(self):
        self.create()

    def __exit__(self, exc_type, exc_value, traceback):
        self.destroy()


class PidFile(Base):
    """
    pidfile scope management

    Usage:
        with PidFile(pidfile):
            pass
    """

    def __init__(self, file):
        super(PidFile, self).__init__()
        self._file = file

    def __enter__(self):
        if self._file is not None:
            self._logger.debug(
                "creating pidfile '%s' pid=%s",
                self._file,
                os.getpid()
            )
            with open(self._file, 'w') as f:
                f.write('%s\n' % os.getpid())

    def __exit__(self, exc_type, exc_value, traceback):
        if self._file is not None:
            self._logger.debug("removing pidfile '%s'", self._file)
            try:
                os.remove(self._file)
            except OSError:
                # we may not have permissions to delete pid
                # so just try to empty it
                try:
                    with open(self._file, 'w'):
                        pass
                except IOError as e:
                    self._logger.error(
                        _("Cannot remove pidfile '{file}': {error}").format(
                            file=self._file,
                            error=e,
                        ),
                    )
                    self._logger.debug('exception', exc_info=True)


class Daemon(Base):

    class TerminateException(Exception):
        pass

    @property
    def pidfile(self):
        return self._options.pidfile

    def __init__(self):
        super(Daemon, self).__init__()

    def check(
        self,
        name,
        mustExist=True,
        readable=True,
        writable=False,
        executable=False,
        directory=False,
    ):
        artifact = _('Directory') if directory else _('File')

        if directory:
            readable = True
            executable = True

        if os.path.exists(name):
            if directory and not os.path.isdir(name):
                raise RuntimeError(
                    _("{artifact} '{name}' is required but missing").format(
                        artifact=artifact,
                        name=name,
                    )
                )
            if readable and not os.access(name, os.R_OK):
                raise RuntimeError(
                    _(
                        "{artifact} '{name}' cannot be accessed "
                        "for reading"
                    ).format(
                        artifact=artifact,
                        name=name,
                    )
                )
            if writable and not os.access(name, os.W_OK):
                raise RuntimeError(
                    _(
                        "{artifact} '{name}' cannot be accessed "
                        "for writing"
                    ).format(
                        artifact=artifact,
                        name=name,
                    )
                )
            if executable and not os.access(name, os.X_OK):
                raise RuntimeError(
                    _(
                        "{artifact} '{name}' cannot be accessed "
                        "for execution"
                    ).format(
                        artifact=artifact,
                        name=name,
                    )
                )
        else:
            if mustExist:
                raise RuntimeError(
                    _("{artifact} '{name}' is required but missing").format(
                        artifact=artifact,
                        name=name,
                    )
                )

            if not os.path.exists(os.path.dirname(name)):
                raise RuntimeError(
                    _(
                        "{artifact} '{name}' is to be created but "
                        "parent directory is missing"
                    ).format(
                        artifact=artifact,
                        name=name,
                    )
                )

            if not os.access(os.path.dirname(name), os.W_OK):
                raise RuntimeError(
                    _(
                        "{artifact} '{name}' is to be created but "
                        "parent directory is not writable"
                    ).format(
                        artifact=artifact,
                        name=name,
                    )
                )

    def daemonAsExternalProcess(
        self,
        executable,
        args,
        env,
        stopTime=30,
        stopInterval=1,
    ):
        self._logger.debug(
            'executing daemon: exe=%s, args=%s, env=%s',
            executable,
            args,
            env,
        )

        try:
            self._logger.debug('creating process')
            p = subprocess.Popen(
                args=args,
                executable=executable,
                env=env,
                close_fds=True,
            )

            self._logger.debug(
                'waiting for termination of pid=%s',
                p.pid,
            )
            p.wait()
            self._logger.debug(
                'terminated pid=%s rc=%s',
                p.pid,
                p.returncode,
            )

            if p.returncode != 0:
                raise RuntimeError(
                    _(
                        'process terminated with status '
                        'code {code}'
                    ).format(
                        code=p.returncode,
                    )
                )

        except self.TerminateException:
            self._logger.debug('got stop signal')

            # avoid recursive signals
            for sig in (signal.SIGTERM, signal.SIGINT):
                signal.signal(sig, signal.SIG_IGN)

            try:
                self._logger.debug('terminating pid=%s', p.pid)
                p.terminate()
                for i in range(stopTime // stopInterval):
                    if p.poll() is not None:
                        self._logger.debug('terminated pid=%s', p.pid)
                        break
                    self._logger.debug(
                        'waiting for termination of pid=%s',
                        p.pid,
                    )
                    time.sleep(stopInterval)
            except OSError as e:
                self._logger.warning(
                    _('Cannot terminate pid {pid}: {error}').format(
                        pid=p.pid,
                        error=e,
                    )
                )
                self._logger.debug('exception', exc_info=True)

            try:
                if p.poll() is None:
                    self._logger.debug('killing pid=%s', p.pid)
                    p.kill()
                    raise RuntimeError(
                        _('Had to kill process {pid}').format(
                            pid=p.pid
                        )
                    )
            except OSError as e:
                self._logger.warning(
                    _('Cannot kill pid {pid}: {error}').format(
                        pid=p.pid,
                        error=e
                    )
                )
                self._logger.debug('exception', exc_info=True)
                raise

            raise

    def _daemon(self):

        self._logger.debug('daemon entry pid=%s', os.getpid())
        self._logger.debug('background=%s', self._options.background)

        self.daemonSetup()

        stdout, stderr = self.daemonStdHandles()

        def _myterm(signum, frame):
            raise self.TerminateException()

        with daemon.DaemonContext(
            detach_process=self._options.background,
            signal_map={
                signal.SIGTERM: _myterm,
                signal.SIGINT: _myterm,
                signal.SIGHUP: None,
            },
            stdout=stdout,
            stderr=stderr,
        ):
            self._logger.debug('I am a daemon %s', os.getpid())

            try:
                with PidFile(self._options.pidfile):
                    self.daemonContext()
            except self.TerminateException:
                self._logger.debug('Terminated normally %s', os.getpid())
            finally:
                self.daemonCleanup()

        self._logger.debug('daemon return')

    def run(self):
        self._logger.debug('startup args=%s', sys.argv)

        parser = optparse.OptionParser(
            usage=_('usage: %prog [options] start'),
        )
        parser.add_option(
            '-d', '--debug',
            dest='debug',
            action='store_true',
            default=False,
            help=_('debug mode'),
        )
        parser.add_option(
            '--pidfile',
            dest='pidfile',
            default=None,
            metavar=_('FILE'),
            help=_('pid file to use'),
        )
        parser.add_option(
            '--background',
            dest='background',
            action='store_true',
            default=False,
            help=_('Go into the background'),
        )
        (self._options, args) = parser.parse_args()

        if self._options.debug:
            logging.getLogger('ovirt').setLevel(logging.DEBUG)

        if len(args) != 1:
            parser.error(_('Action is missing'))
        action = args[0]
        if not action in ('start'):
            parser.error(
                _("Invalid action '{action}'").format(
                    action=action
                )
            )

        try:
            self._daemon()
        except Exception as e:
            self._logger.error(
                _('Error: {error}').format(
                    error=e,
                )
            )
            self._logger.debug('exception', exc_info=True)
            sys.exit(1)
        else:
            sys.exit(0)

    def daemonSetup(self):
        """Setup environment
        Called before daemon context
        """
        pass

    def daemonStdHandles(self):
        """Return handles for daemon context"""
        return (sys.stdout, sys.stderr)

    def daemonContext(self):
        """Daemon logic
        Called within daemon context
        """
        pass

    def daemonCleanup(self):
        """Cleanup"""
        pass


# vim: expandtab tabstop=4 shiftwidth=4