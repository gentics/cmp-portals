<?php

namespace Gentics\PortalPhp\Tests\Unit\Middleware;

use Gentics\PortalPhp\Tests\TestCase;

class GenticsHttpHeadersTest extends TestCase
{
    /**
     * Test that the response header "X-Powered-By" is set to "gentics.com"
     */
    public function testXpoweredbyHeader()
    {
        $response = $this->call('GET', '/api/health/live');
        $response->assertHeader('X-Powered-By', 'gentics.com');
    }
}
