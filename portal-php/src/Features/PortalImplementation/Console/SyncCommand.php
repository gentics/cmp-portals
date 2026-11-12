<?php

namespace Gentics\PortalPhp\Features\PortalImplementation\Console;

use Gentics\PortalPhp\Features\PortalImplementation\PortalImplementationService;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Log;

/**
 * Loads the configured portal implementation from Mesh, when it changed. Must be run when the portal is started and
 * is scheduled every minute.
 */
class SyncCommand extends Command
{
    /**
     * @var string
     */
    protected $signature = 'portal:implementation:sync';

    /**
     * @var string
     */
    protected $description = 'Load the configured portal implementation from Mesh, when it changed';

    /**
     * @param PortalImplementationService $service
     * @return int
     */
    public function handle(PortalImplementationService $service): int
    {
        if (!$service->isEnabled()) {
            $this->info('No portal implementation configured');
            return self::SUCCESS;
        }

        try {
            $status = $service->sync();
        } catch (\Throwable $e) {
            Log::error(sprintf(
                'Could not load portal implementation %s, using local files: %s',
                $service->getKeyword(),
                $e->getMessage()
            ), ['exception' => $e]);
            $this->error(sprintf(
                'Could not load portal implementation %s: %s',
                $service->getKeyword(),
                $e->getMessage()
            ));
            return self::FAILURE;
        }

        $messages = [
            PortalImplementationService::STATUS_NOT_FOUND => 'Portal implementation %s not found, using local files',
            PortalImplementationService::STATUS_UNCHANGED => 'Portal implementation %s is unchanged',
            PortalImplementationService::STATUS_UPDATED => 'Portal implementation %s was updated',
        ];

        $this->info(sprintf($messages[$status] ?? 'Portal implementation %s: ' . $status, $service->getKeyword()));

        return self::SUCCESS;
    }
}
