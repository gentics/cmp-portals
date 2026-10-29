<?php

namespace Gentics\PortalPhp\Features\Keycloak\Storage;

use Carbon\Carbon;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Illuminate\Contracts\Cache\Repository as CacheRepository;

use Lcobucci\JWT\Decoder;
use Lcobucci\JWT\Signer\Key;
use Lcobucci\JWT\Signer\Key\InMemory;

/**
 * Store and get authentication server validation keys
 *
 * Class Keystore
 * @package Gentics\PortalPhp\Features\Keycloak\Storage
 */
class Keystore
{
    /**
     * @var Decoder
     */
    private $decoder;
    /**
     * @var CacheRepository
     */
    private $cache;

    /**
     * Keystore constructor.
     *
     * @param CacheRepository $cache
     * @param Decoder         $decoder
     */
    public function __construct(CacheRepository $cache, Decoder $decoder)
    {
        $this->cache = $cache;
        $this->decoder = $decoder;
    }

    /**
     * Fetch JWK key from JWKs URI with defined kid
     *
     * @param string $kid
     *
     * @return Key|null
     */
    public function get(string $kid): ?Key
    {
        $cacheKey = 'keycloak-jwks.' . $kid;

        if ($this->cache->has($cacheKey)) {
            return $this->cache->get($cacheKey);
        }

        /** @var Key[] $keys */
        $keys = $this->fetch();

        if (!isset($keys[$kid])) {
            return null;
        }

        $this->cache->put($cacheKey, $keys[$kid], Carbon::now()->addHours(6));

        return $keys[$kid];
    }

    /**
     * Fetch list of JWKs from JWKs URI
     *
     * @return array
     */
    public function fetch(): array
    {
        $keys = [];

        $data = \Socialite::with('keycloak')->getJwks();

        if (!isset($data['keys'])) {
            throw new PortalPhpException('"keys" member must exist in the JWK Set');
        }

        if (empty($data['keys'])) {
            throw new PortalPhpException('JWK Set did not contain any keys');
        }

        foreach ($data['keys'] as $k => $v) {
            $kid = isset($v['kid']) ? $v['kid'] : $k;
            if ($key = $this->parseKey($v)) {
                $keys[$kid] = $key;
            }
        }

        if (0 === \count($keys)) {
            throw new PortalPhpException('No supported algorithms found in JWK Set');
        }

        return $keys;
    }

    protected function parseKey($key): ?Key
    {
        if (empty($key)) {
            throw new PortalPhpException('JWK must not be empty');
        }

        if (!isset($key['kty'])) {
            throw new PortalPhpException('JWK must contain a "kty" parameter');
        }

        switch ($key['kty']) {
            case 'RSA':
                if (isset($key['x5c'])) {
                    $pem = $this->createPemFromX5c(last($key['x5c']));
                } else {
                    // For older Keycloak versions
                    $pem = $this->createPemFromModulusAndExponent($key['n'], $key['e']);
                }

                return InMemory::plainText($pem);
            default:
                // Currently only RSA is supported
                break;
        }
    }


    /**
     *
     * Create a public key represented in PEM format from the x5c field information
     *
     * @param array $x5c the x5c field encoded with base64
     *
     * @return string the RSA public key represented in PEM format
     */
    protected function createPemFromX5c(string $x5c): string
    {
        $CurrRSAPublicKey  = "-----BEGIN CERTIFICATE-----" . PHP_EOL;
        $CurrRSAPublicKey .= chunk_split($x5c, 64, PHP_EOL);
        $CurrRSAPublicKey .= '-----END CERTIFICATE-----';

        $ExtRSAPublicKey   = openssl_pkey_get_details(openssl_pkey_get_public($CurrRSAPublicKey));

        $RSAPublicKey      = trim($ExtRSAPublicKey['key']);

        return $RSAPublicKey;
    }

    /**
     *
     * Create a public key represented in PEM format from RSA modulus and exponent information
     *
     * @param string $n the RSA modulus encoded in URL Safe Base64
     * @param string $e the RSA exponent encoded in URL Safe Base64
     *
     * @return string the RSA public key represented in PEM format
     */
    protected function createPemFromModulusAndExponent(string $n, string $e): string
    {
        $modulus = $this->decoder->base64UrlDecode($n);
        $publicExponent = $this->decoder->base64UrlDecode($e);

        $components = [
            'modulus' => pack('Ca*a*', 2, $this->encodeLength(strlen($modulus)), $modulus),
            'publicExponent' => pack('Ca*a*', 2, $this->encodeLength(strlen($publicExponent)), $publicExponent),
        ];

        $RSAPublicKey = pack(
            'Ca*a*a*',
            48,
            $this->encodeLength(strlen($components['modulus']) + strlen($components['publicExponent'])),
            $components['modulus'],
            $components['publicExponent']
        );

        $rsaOID = pack('H*', '300d06092a864886f70d0101010500');
        $RSAPublicKey = chr(0) . $RSAPublicKey;
        $RSAPublicKey = chr(3) . $this->encodeLength(strlen($RSAPublicKey)) . $RSAPublicKey;
        $RSAPublicKey = pack(
            'Ca*a*',
            48,
            $this->encodeLength(strlen($rsaOID . $RSAPublicKey)),
            $rsaOID . $RSAPublicKey
        );

        $RSAPublicKey = "-----BEGIN PUBLIC KEY-----" . PHP_EOL
            . chunk_split(base64_encode($RSAPublicKey), 64, PHP_EOL)
            . '-----END PUBLIC KEY-----';

        return $RSAPublicKey;
    }

    /**
     * DER-encode the length
     *
     * DER supports lengths up to (2**8)**127, however, we'll only support lengths up to (2**8)**4.  See
     * {@link http://itu.int/ITU-T/studygroups/com17/languages/X.690-0207.pdf#p=13 X.690 paragraph 8.1.3}
     * for more information.
     *
     * @param int $length
     *
     * @return string
     */
    protected function encodeLength(int $length): string
    {
        if ($length <= 0x7F) {
            return chr($length);
        }

        $temp = ltrim(pack('N', $length), chr(0));
        return pack('Ca*', 0x80 | strlen($temp), $temp);
    }
}
