<?php

namespace Gentics\PortalPhp\Features\PortalImplementation;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Illuminate\Filesystem\Filesystem;
use Illuminate\Support\Facades\Log;
use ZipArchive;

/**
 * Extracts the package (zip file) of a portal implementation into the target folders.
 *
 * The folders views, graphql and lang of the package replace the contents of their target folders. Target folders,
 * whose folder is missing in the package, are emptied.
 *
 * The folder public is copied over the target folder (the public folder of the portal), so that the static files of
 * the portal instance are kept. The files provided by the portal implementation are recorded in a manifest, so that
 * they can be removed again with the next update. Files of the portal instance, which are overwritten by the portal
 * implementation, are backed up and restored, when the portal implementation does no longer contain them.
 *
 * All other entries of the package are ignored (with a warning). The package is completely unzipped before any target
 * folder is changed, so an invalid package leaves all target folders untouched.
 */
class PortalImplementationExtractor
{
    public const FOLDER_VIEWS = 'views';
    public const FOLDER_GRAPHQL = 'graphql';
    public const FOLDER_LANG = 'lang';
    public const FOLDER_PUBLIC = 'public';

    public const KNOWN_FOLDERS = [
        self::FOLDER_VIEWS,
        self::FOLDER_GRAPHQL,
        self::FOLDER_LANG,
        self::FOLDER_PUBLIC,
    ];

    /** Name of the manifest file (in the state folder) listing the files copied into the public folder */
    public const PUBLIC_MANIFEST = 'public-manifest.json';

    /** Name of the folder (in the state folder) holding the overwritten files of the public folder */
    public const PUBLIC_BACKUP = 'public-backup';

    /**
     * @var Filesystem
     */
    protected $files;

    /**
     * @param Filesystem|null $files
     */
    public function __construct(?Filesystem $files = null)
    {
        $this->files = $files ?? new Filesystem();
    }

    /**
     * Extract the package into the target folders.
     *
     * @param string $zipFile Path to the package
     * @param array $targets Map of package folder names to the target paths
     * @param string $stateDir Folder for temporary files, the manifest and the backups of the public folder
     * @return string[] Names of the folders, which were contained in the package
     * @throws PortalPhpException When the package is not a valid zip file or contains invalid entries
     */
    public function extract(string $zipFile, array $targets, string $stateDir): array
    {
        $this->files->ensureDirectoryExists($stateDir);
        $tmpDir = $stateDir . '/tmp-' . bin2hex(random_bytes(8));

        try {
            $this->unzip($zipFile, $tmpDir);

            $extracted = [];

            foreach ($targets as $folder => $target) {
                $source = $tmpDir . '/' . $folder;

                if ($folder === self::FOLDER_PUBLIC) {
                    $this->overlayPublic(is_dir($source) ? $source : null, $target, $stateDir);
                } elseif (is_dir($source)) {
                    $this->files->ensureDirectoryExists($target);
                    $this->files->cleanDirectory($target);
                    $this->files->copyDirectory($source, $target);
                } elseif (is_dir($target)) {
                    // The folder is not part of the portal implementation, so no local files must be used
                    $this->files->cleanDirectory($target);
                }

                if (is_dir($source)) {
                    $extracted[] = $folder;
                }
            }

            return $extracted;
        } finally {
            $this->files->deleteDirectory($tmpDir);
        }
    }

    /**
     * Unzip the known folders of the package into the given folder.
     *
     * @param string $zipFile
     * @param string $targetDir
     * @throws PortalPhpException
     */
    protected function unzip(string $zipFile, string $targetDir): void
    {
        $zip = new ZipArchive();
        $result = $zip->open($zipFile, ZipArchive::RDONLY);

        if ($result !== true) {
            throw new PortalPhpException(sprintf('Package %s is not a valid zip file (error %s)', $zipFile, $result));
        }

        try {
            $ignored = [];

            for ($i = 0; $i < $zip->numFiles; $i++) {
                $name = str_replace('\\', '/', $zip->getNameIndex($i));
                $segments = explode('/', $name);

                $absolute = str_starts_with($name, '/') || preg_match('/^[A-Za-z]:/', $name);

                if ($absolute || in_array('..', $segments, true)) {
                    throw new PortalPhpException(
                        sprintf('Entry %s of the package points outside of its folder', $name)
                    );
                }

                $folder = $segments[0];

                if (count($segments) < 2 || !in_array($folder, self::KNOWN_FOLDERS, true)) {
                    if (!isset($ignored[$folder])) {
                        $ignored[$folder] = true;
                        Log::warning(sprintf(
                            'Ignoring %s of the portal implementation package, only the folders %s are used',
                            $folder,
                            implode(', ', self::KNOWN_FOLDERS)
                        ));
                    }

                    continue;
                }

                $path = $targetDir . '/' . $name;

                if (str_ends_with($name, '/')) {
                    $this->files->ensureDirectoryExists($path);
                    continue;
                }

                $this->files->ensureDirectoryExists(dirname($path));
                $stream = $zip->getStream($zip->getNameIndex($i));

                if ($stream === false) {
                    throw new PortalPhpException(sprintf('Could not read entry %s of the package', $name));
                }

                try {
                    $out = fopen($path, 'wb');
                    stream_copy_to_stream($stream, $out);
                    fclose($out);
                } finally {
                    fclose($stream);
                }
            }
        } finally {
            $zip->close();
        }
    }

    /**
     * Copy the files of the package's public folder over the public folder of the portal.
     *
     * @param string|null $source Public folder of the package, null when the package has none
     * @param string $target Public folder of the portal
     * @param string $stateDir Folder holding the manifest and the backups
     */
    protected function overlayPublic(?string $source, string $target, string $stateDir): void
    {
        $manifestFile = $stateDir . '/' . self::PUBLIC_MANIFEST;
        $backupDir = $stateDir . '/' . self::PUBLIC_BACKUP;
        $previous = is_file($manifestFile) ? (json_decode($this->files->get($manifestFile), true) ?: []) : [];
        $current = [];

        if ($source !== null) {
            foreach ($this->files->allFiles($source, true) as $file) {
                $current[] = str_replace('\\', '/', $file->getRelativePathname());
            }
        }

        // Remove the files no longer provided by the portal implementation, and restore the overwritten files
        foreach (array_diff($previous, $current) as $path) {
            $this->files->delete($target . '/' . $path);

            if (is_file($backupDir . '/' . $path)) {
                $this->files->ensureDirectoryExists(dirname($target . '/' . $path));
                $this->files->move($backupDir . '/' . $path, $target . '/' . $path);
            }
        }

        // Back up the files of the portal instance, which will be overwritten
        foreach (array_diff($current, $previous) as $path) {
            if (is_file($target . '/' . $path)) {
                $this->files->ensureDirectoryExists(dirname($backupDir . '/' . $path));
                $this->files->move($target . '/' . $path, $backupDir . '/' . $path);
            }
        }

        foreach ($current as $path) {
            $this->files->ensureDirectoryExists(dirname($target . '/' . $path));
            $this->files->copy($source . '/' . $path, $target . '/' . $path);
        }

        $this->files->put($manifestFile, json_encode(array_values($current), JSON_PRETTY_PRINT));
    }
}
